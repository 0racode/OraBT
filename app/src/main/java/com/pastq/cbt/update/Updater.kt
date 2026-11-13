package com.pastq.cbt.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pastq.cbt.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File

@Serializable
data class GhAsset(val name: String = "", val browser_download_url: String = "", val size: Long = 0)

@Serializable
data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GhAsset> = emptyList(),
)

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latest(@Path("owner") owner: String, @Path("repo") repo: String): GhRelease
}

data class UpdateInfo(val code: Int, val name: String, val notes: String, val url: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState

    /** Waiting for the user to allow installs. [installFile] is set when the file is already downloaded. */
    data class NeedsPermission(val info: UpdateInfo, val installFile: File?) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * In-app updater.
 *
 * Design goals:
 *  - The install permission is requested BEFORE the download starts, so the trip to Android
 *    settings can never interrupt a download.
 *  - The download is handled by the system DownloadManager, which keeps going (and keeps the
 *    file) even if this app is backgrounded or killed. Progress is re-read from the system
 *    whenever the app comes back, so nothing has to be downloaded twice.
 */
object Updater {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var pollJob: Job? = null
    private var latest: UpdateInfo? = null
    private var foreground = false

    private val prefs get() = app.getSharedPreferences("updater", Context.MODE_PRIVATE)

    private val github: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }

    fun init(context: Context) {
        app = context.applicationContext
        cleanOldFiles()
    }

    fun setForeground(value: Boolean) {
        foreground = value
    }

    private fun downloadsDir(): File? = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    private fun fileFor(code: Int): File = File(downloadsDir(), "PastQ-CBT-$code.apk")

    private fun cleanOldFiles() {
        downloadsDir()?.listFiles()?.forEach { f ->
            val code = f.name.removePrefix("PastQ-CBT-").removeSuffix(".apk").toIntOrNull()
            if (code != null && code <= BuildConfig.VERSION_CODE) f.delete()
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    /** Opens the Android screen where the user allows this app to install updates. */
    fun openInstallSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    /** Looks for a newer release on GitHub. */
    fun check() {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.NeedsPermission) return
        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val rel = withContext(Dispatchers.IO) {
                    github.latest(BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO)
                }
                val code = rel.tag_name.removePrefix("build-").toIntOrNull()
                val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") }
                if (code == null || apk == null || code <= BuildConfig.VERSION_CODE) {
                    _state.value = UpdateState.UpToDate
                    return@launch
                }
                val info = UpdateInfo(code, rel.name.ifBlank { "Version $code" }, rel.body.trim(), apk.browser_download_url, apk.size)
                latest = info
                if (!resumeExisting(info)) _state.value = UpdateState.Available(info)
            } catch (e: HttpException) {
                // 404 means no release has been published yet
                _state.value = if (e.code() == 404) UpdateState.UpToDate
                else UpdateState.Failed("Could not check for updates (${e.code()}).")
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Could not check for updates. Check your connection.")
            }
        }
    }

    /** User tapped "Update". Asks for install permission first, then downloads. */
    fun startUpdate() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.NeedsPermission -> s.info
            is UpdateState.Failed -> latest
            else -> null
        }
        if (info == null) {
            check()
            return
        }
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info, null)
            return
        }
        beginDownload(info)
    }

    /** User tapped "Install now" on an already downloaded update. */
    fun installNow() {
        val s = _state.value
        if (s is UpdateState.Ready) install(s.info, s.file)
    }

    /** Call from the activity's onResume. Picks up where things were left, e.g. after the settings screen. */
    fun onResume() {
        when (val s = _state.value) {
            is UpdateState.NeedsPermission -> if (canInstall()) {
                if (s.installFile != null) install(s.info, s.installFile) else beginDownload(s.info)
            }
            is UpdateState.Downloading -> startPolling(s.info)
            is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Failed -> check()
            else -> Unit
        }
    }

    fun dismiss() {
        val s = _state.value
        if (s is UpdateState.Failed || s is UpdateState.UpToDate) _state.value = UpdateState.Idle
    }

    // ---- internals -------------------------------------------------------------------------

    private fun dm() = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** If this version is already downloaded (or downloading), reuse it instead of starting over. */
    private fun resumeExisting(info: UpdateInfo): Boolean {
        val file = fileFor(info.code)
        if (file.exists() && info.size > 0 && file.length() == info.size) {
            _state.value = UpdateState.Ready(info, file)
            return true
        }
        val id = prefs.getLong("id", -1)
        if (id != -1L && prefs.getInt("code", -1) == info.code) {
            dm().query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status != DownloadManager.STATUS_FAILED) {
                        _state.value = UpdateState.Downloading(info, 0)
                        startPolling(info)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun beginDownload(info: UpdateInfo) {
        if (resumeExisting(info)) {
            val s = _state.value
            if (s is UpdateState.Ready && foreground) install(s.info, s.file)
            return
        }
        val file = fileFor(info.code)
        file.delete()
        val request = DownloadManager.Request(Uri.parse(info.url))
            .setTitle("PastQ CBT ${info.name}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, file.name)
        val id = dm().enqueue(request)
        prefs.edit().putLong("id", id).putInt("code", info.code).apply()
        _state.value = UpdateState.Downloading(info, 0)
        startPolling(info)
    }

    private fun startPolling(info: UpdateInfo) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            loop@ while (true) {
                val id = prefs.getLong("id", -1)
                var status = -1
                var done = 0L
                var total = 0L
                var found = false
                dm().query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (c.moveToFirst()) {
                        found = true
                        status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    }
                }
                if (!found) {
                    prefs.edit().clear().apply()
                    _state.value = UpdateState.Failed("The download was cancelled. Tap Update to try again.")
                    break@loop
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = fileFor(info.code)
                        _state.value = UpdateState.Ready(info, file)
                        if (foreground) install(info, file)
                        break@loop
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.edit().clear().apply()
                        _state.value = UpdateState.Failed("The download failed. Check your connection and try again.")
                        break@loop
                    }
                    else -> {
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        _state.value = UpdateState.Downloading(info, pct)
                    }
                }
                delay(500)
            }
        }
    }

    private fun install(info: UpdateInfo, file: File) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info, file)
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        app.startActivity(intent)
    }
}
