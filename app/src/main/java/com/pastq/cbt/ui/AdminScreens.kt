@file:OptIn(ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.AdminRequests
import com.pastq.cbt.data.AdminResults
import com.pastq.cbt.data.AdminSession
import com.pastq.cbt.data.DisputedItem
import com.pastq.cbt.data.DraftItem
import com.pastq.cbt.data.JoinRequest
import com.pastq.cbt.data.LiveManager
import com.pastq.cbt.data.QueueCounts
import com.pastq.cbt.data.ReportInfo
import com.pastq.cbt.data.Reporter
import com.pastq.cbt.data.Repository
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminLoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val admin by LiveManager.admin.collectAsStateWithLifecycle()
    LaunchedEffect(admin) { if (admin != null) onLoggedIn() }

    Scaffold(
        topBar = { BackBar("Admin sign in", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(
                    enabled = !busy && login.isNotBlank() && password.length >= 4,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            LiveManager.adminLogin(login, password).onFailure { error = it.friendlyMessage() }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Please wait..." else "Sign in") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in with the admin email or name.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(login, { login = it }, label = { Text("Email or name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun AdminHomeScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onResults: (String) -> Unit,
    onDisputed: () -> Unit,
) {
    val admin by LiveManager.admin.collectAsStateWithLifecycle()
    var sessions by remember { mutableStateOf<List<AdminSession>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<AdminSession?>(null) }
    var requestsFor by remember { mutableStateOf<AdminSession?>(null) }
    var schedulingFor by remember { mutableStateOf<AdminSession?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(admin) { if (admin == null) onBack() }
    LaunchedEffect(reload) {
        while (true) {
            try {
                sessions = LiveManager.adminList().sessions
                error = null
            } catch (e: Exception) {
                error = e.friendlyMessage()
            }
            delay(5000)
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try { block(); error = null } catch (e: Exception) { error = e.friendlyMessage() }
            reload++
        }
    }

    requestsFor?.let { s -> RequestsDialog(s) { requestsFor = null; reload++ } }

    schedulingFor?.let { s ->
        val current = parseIsoUtc(s.scheduled_start_at)
        var pick by remember(s.id) { mutableStateOf(current?.takeIf { it > System.currentTimeMillis() } ?: defaultStartMillis()) }
        AlertDialog(
            onDismissRequest = { schedulingFor = null },
            title = { Text("Start time for \"${s.title}\"") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StartTimeField(pick) { pick = it }
                    Text(
                        "Starts at ${formatWhen(pick)} and ends at ${formatWhen(pick + s.minutes * 60_000L)}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (pick <= System.currentTimeMillis()) {
                        Text("Pick a time in the future.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pick > System.currentTimeMillis(),
                    onClick = { schedulingFor = null; act { LiveManager.adminSchedule(s.id, isoUtc(pick)) } },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (current != null) {
                        TextButton(onClick = { schedulingFor = null; act { LiveManager.adminSchedule(s.id, null) } }) { Text("Remove time") }
                    }
                    TextButton(onClick = { schedulingFor = null }) { Text("Cancel") }
                }
            },
        )
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${s.title}\"?") },
            text = { Text("Its questions, players and results are removed.") },
            confirmButton = { TextButton(onClick = { deleting = null; act { LiveManager.adminDelete(s.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = { BackBar("Live tests (admin)", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create a live test") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDisputed, modifier = Modifier.weight(1f)) { Text("Review answer keys") }
                    OutlinedButton(onClick = { LiveManager.signOutAdmin() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val list = sessions
            when {
                list == null && error == null -> CircularProgressIndicator()
                list != null && list.isEmpty() -> Text("No live tests yet. Create one below.")
                list != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list) { s ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text("Code: ${s.code}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                val startMs = parseIsoUtc(s.scheduled_start_at)
                                val state = when (s.status) {
                                    "waiting" ->
                                        if (startMs != null) (if (s.open_window_min != null) "Opens " else "Starts ") + "${formatWhen(startMs)} (${countdownLabel(((startMs - System.currentTimeMillis()) / 1000).toInt())})"
                                        else "Waiting to start"
                                    "running" -> if (s.open_window_min != null) "Open · closes in ${countdownLabel(s.seconds_left)}" else "Running · ${formatTime(s.seconds_left)} left"
                                    else -> "Ended"
                                }
                                Text("$state · ${s.questions} questions · ${s.minutes} min" + if (s.open_window_min != null) " each" else "", style = MaterialTheme.typography.bodyMedium)
                                Text("${s.players} joined · ${s.submitted} submitted" + if (s.auto_end) " · ends when all submit" else "", style = MaterialTheme.typography.bodySmall)
                                if (s.requests > 0) {
                                    Text("${s.requests} request(s) waiting for you", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                if (s.subjects.isNotBlank()) Text(s.subjects, style = MaterialTheme.typography.bodySmall)
                                if (s.status == "waiting") {
                                    OutlinedButton(onClick = { schedulingFor = s }) { Text(if (s.scheduled_start_at != null) "Change start time" else "Set a start time") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (s.status == "waiting") Button(onClick = { act { LiveManager.adminStart(s.id) } }) { Text(if (s.open_window_min != null) "Open now" else if (s.scheduled_start_at != null) "Start now" else "Start") }
                                    if (s.status == "running") Button(onClick = { act { LiveManager.adminEnd(s.id) } }) { Text("End now") }
                                    if ((s.status == "running" && s.open_window_min == null) || s.requests > 0) {
                                        OutlinedButton(onClick = { requestsFor = s }) { Text(if (s.requests > 0) "Requests (${s.requests})" else "Requests") }
                                    }
                                    OutlinedButton(onClick = { onResults(s.id) }) { Text("Results") }
                                    TextButton(onClick = { deleting = s }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Late-join requests and come-back requests for one test, with Allow and Decline for each. */
@Composable
private fun RequestsDialog(session: AdminSession, onClose: () -> Unit) {
    var data by remember { mutableStateOf<AdminRequests?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.id) {
        while (true) {
            try { data = LiveManager.adminRequests(session.id); error = null } catch (e: Exception) { error = e.friendlyMessage() }
            delay(3000)
        }
    }

    fun decide(r: JoinRequest, allow: Boolean) {
        scope.launch {
            busyId = r.player_id
            try {
                LiveManager.adminDecide(session.id, r.player_id, allow)
                data = LiveManager.adminRequests(session.id)
                error = null
            } catch (e: Exception) {
                error = e.friendlyMessage()
            }
            busyId = null
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Requests: ${session.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val d = data
                when {
                    d == null && error == null -> CircularProgressIndicator()
                    d != null && d.requests.isEmpty() -> Text("No requests waiting. New ones appear here by themselves.")
                    d != null -> d.requests.forEach { r ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (r.kind == "join") "Asking to join after the start"
                                    else "Left the app during the test and asks to come back",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = busyId == null, onClick = { decide(r, true) }) { Text("Allow") }
                                    OutlinedButton(enabled = busyId == null, onClick = { decide(r, false) }) { Text("Decline") }
                                }
                                if (r.kind == "rejoin") {
                                    Text("Decline grades the answers they already gave and ends their test.", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

private const val MAX_TEST_SUBJECTS = 4

private data class PasteBlock(val subject: String = "", val text: String = "")

private data class ManualDraft(
    val question: String = "",
    val section: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val explanation: String = "",
    val answer: Char? = null,
    val note: String? = null,
    val subject: String = "",
) {
    val valid get() = question.isNotBlank() && a.isNotBlank() && b.isNotBlank() && c.isNotBlank() && d.isNotBlank() && answer != null
}

@Composable
fun AdminCreateScreen(repo: Repository, onBack: () -> Unit, onCreated: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var title by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("0") }
    var mins by remember { mutableStateOf("40") }
    val picks = remember { mutableStateMapOf<String, String>() }
    val manual = remember { mutableStateListOf<ManualDraft>() }
    val pasteBlocks = remember { mutableStateListOf(PasteBlock()) }
    var drafting by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var announce by remember { mutableStateOf(true) }
    var timerMode by remember { mutableStateOf("shared") }
    var joinWindow by remember { mutableStateOf("10") }
    var scoreVisibility by remember { mutableStateOf("after_end") }
    var customCode by remember { mutableStateOf("") }
    var autoEnd by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(false) }
    var startMillis by remember { mutableStateOf(defaultStartMillis()) }
    var autoApproveLate by remember { mutableStateOf(false) }
    var strictLeave by remember { mutableStateOf(false) }
    var strictReturn by remember { mutableStateOf(false) }
    // Open-window test: no admin Start; players start any time inside the window (up to 2 days).
    var openType by remember { mutableStateOf(false) }
    var openDays by remember { mutableStateOf("1") }
    var openHours by remember { mutableStateOf("0") }
    var openMins by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importPreview by remember { mutableStateOf<LiveImport?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var sampleText by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                importPreview = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.buffered().readBytes() }
                        ?: throw IllegalArgumentException("Couldn't open that file.")
                    if (bytes.size > LiveImportParser.MAX_BYTES) throw IllegalArgumentException("That file is too big (1 MB at most).")
                    LiveImportParser.parse(String(bytes, Charsets.UTF_8))
                }
            } catch (e: Exception) {
                error = "Couldn't read the file: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun writeSample(uri: android.net.Uri?) {
        val text = sampleText
        sampleText = null
        if (uri == null || text == null) return
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            error = null
        } catch (e: Exception) {
            error = "Couldn't save the file: ${e.message}"
        }
    }
    val saveSampleCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { writeSample(it) }
    val saveSampleJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { writeSample(it) }

    importPreview?.let { p ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("Import from file") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${p.questions.size} question(s) ready to add" + if (p.problems.isNotEmpty()) ", ${p.problems.size} skipped." else ".")
                    p.title?.let { Text("Title from file: $it", style = MaterialTheme.typography.bodySmall) }
                    p.minutes?.let { Text("Time from file: $it minutes", style = MaterialTheme.typography.bodySmall) }
                    p.problems.take(8).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (p.problems.size > 8) Text("…and ${p.problems.size - 8} more skipped.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "They are added to any questions already on this page. Check the answers before you create the test.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.questions.isNotEmpty(),
                    onClick = {
                        manual.addAll(p.questions.map {
                            ManualDraft(
                                question = it.question, section = it.section, a = it.a, b = it.b, c = it.c, d = it.d,
                                explanation = it.explanation, answer = it.answer, note = "Imported from file. Check it.", subject = it.subject,
                            )
                        })
                        if (title.isBlank()) p.title?.let { title = it }
                        p.minutes?.let { hours = (it / 60).toString(); mins = (it % 60).toString() }
                        importPreview = null
                    },
                ) { Text("Add ${p.questions.size}") }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Cancel") } },
        )
    }

    if (showGuide) {
        val mono = androidx.compose.ui.text.font.FontFamily.Monospace
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("Import file format") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Use a .csv or .json file with one entry per question. Every question needs the question text, four options (a, b, c, d) and the correct answer (a, b, c or d). Optional: subject, explanation, and section (a passage or instructions).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sampleText = LiveImportParser.SAMPLE_CSV; saveSampleCsv.launch("pastq_live_sample.csv") }) { Text("Save sample CSV") }
                        OutlinedButton(onClick = { sampleText = LiveImportParser.SAMPLE_JSON; saveSampleJson.launch("pastq_live_sample.json") }) { Text("Save sample JSON") }
                    }
                    Text("CSV", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "First row is the header. Exports from Excel and Google Sheets work. Put text that contains commas inside quotes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(LiveImportParser.SAMPLE_CSV, fontFamily = mono, style = MaterialTheme.typography.labelSmall)
                    Text("JSON", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "A list of questions, or an object with title, minutes (1 to 120), subject and a questions list. Options can be a, b, c, d or one options list. The answer can also be 1 to 4.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(LiveImportParser.SAMPLE_JSON, fontFamily = mono, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Text can use <b>, <i>, <u> and &nbsp; formatting. A test can hold up to 200 questions. Rows with a problem are skipped and listed before anything is added.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showGuide = false }) { Text("Close") } },
        )
    }

    val minutes = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val timeOk = minutes in 1..120
    val openWindowMin = (openDays.toIntOrNull() ?: 0) * 1440 + (openHours.toIntOrNull() ?: 0) * 60 + (openMins.toIntOrNull() ?: 0)
    val openOk = !openType || (openWindowMin in 1..2880 && minutes <= openWindowMin)
    val pickTotal = picks.values.sumOf { it.toIntOrNull() ?: 0 }
    val total = pickTotal + manual.size
    // Up to 4 subjects share one test and one timer. Names that differ only by case or spacing count as one.
    val subjectKeys = (picks.filter { (it.value.toIntOrNull() ?: 0) > 0 }.keys + manual.map { it.subject.ifBlank { "Custom" } })
        .map { it.trim().lowercase().replace(Regex("\\s+"), " ") }.toSet()
    val subjectCount = subjectKeys.size
    val subjectsOk = subjectCount <= MAX_TEST_SUBJECTS
    val windowOk = timerMode != "personal" || (joinWindow.toIntOrNull() ?: -1) in 0..60
    val codeOk = customCode.isEmpty() || customCode.length in 4..12
    val startOk = !scheduled || startMillis > System.currentTimeMillis()
    val canCreate = !creating && timeOk && windowOk && codeOk && startOk && subjectsOk && openOk && total in 1..200 && manual.all { it.valid }

    Scaffold(
        topBar = { BackBar("New live test", onBack) },
        bottomBar = {
            BottomActionBar {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!subjectsOk) Text("A test can have at most $MAX_TEST_SUBJECTS subjects. This one has $subjectCount.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("$total question(s) · $subjectCount subject(s) · ${if (timeOk) "$minutes minutes" else "set a time"}", style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = canCreate,
                    onClick = {
                        scope.launch {
                            creating = true; error = null
                            try {
                                LiveManager.adminCreate(
                                    title = title.ifBlank { "Live test" },
                                    minutes = minutes,
                                    picks = picks.mapValues { it.value.toIntOrNull() ?: 0 },
                                    manual = manual.map { DraftItem(it.question, it.section, it.a, it.b, it.c, it.d, "", it.explanation, false, true, it.subject) },
                                    answers = manual.map { it.answer ?: 'a' },
                                    announce = announce,
                                    timerMode = if (openType) "personal" else timerMode,
                                    openWindowMin = if (openType) openWindowMin else 0,
                                    joinWindowMin = joinWindow.toIntOrNull() ?: 10,
                                    scoreVisibility = scoreVisibility,
                                    code = customCode,
                                    autoEnd = autoEnd && !openType,
                                    startAt = if (scheduled) isoUtc(startMillis) else null,
                                    autoApproveLate = scheduled && autoApproveLate,
                                    strictLeave = strictLeave,
                                    strictReturn = strictLeave && strictReturn,
                                )
                                onCreated()
                            } catch (e: Exception) {
                                error = e.friendlyMessage()
                            }
                            creating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (creating) "Creating..." else if (scheduled) "Schedule test" else "Create session") }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                customCode, { customCode = it.filter { ch -> ch.isLetterOrDigit() && ch.code < 128 }.take(12).uppercase() },
                label = { Text("Join code (optional, 4 to 12 letters or digits)") }, singleLine = true,
                isError = !codeOk,
                supportingText = { Text("Leave empty for a random code. Pick something easy to say out loud, like MATHS101.") },
                modifier = Modifier.fillMaxWidth(),
            )
            DurationInput(hours, mins, { hours = it }, { mins = it }, timeOk)

            Text("How does the test start?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !openType, onClick = { openType = false }, label = { Text("I start it") })
                FilterChip(selected = openType, onClick = { openType = true }, label = { Text("Open window") })
            }
            if (openType) {
                Text(
                    "Players start the test themselves, any time while the window is open, so you do not need to press Start. Each player gets the time above from the moment they start. Nobody can start after the window closes, and anyone still testing when it closes is stopped at once.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Window stays open for", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(openDays, { openDays = it.filter(Char::isDigit).take(1) }, label = { Text("Days") }, singleLine = true,
                        isError = !openOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(openHours, { openHours = it.filter(Char::isDigit).take(2) }, label = { Text("Hours") }, singleLine = true,
                        isError = !openOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(openMins, { openMins = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, singleLine = true,
                        isError = !openOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                if (!openOk) {
                    Text(
                        if (openWindowMin > 2880) "The window can be 2 days at most."
                        else if (openWindowMin < 1) "Set how long the window stays open."
                        else "The window must be at least as long as the time each player gets.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Open for ${formatOpenWindow(openWindowMin)}.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Start at a set time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (scheduled) "The test starts by itself at the time below, even if you are offline."
                        else if (openType) "Off: the window opens as soon as you create the test."
                        else "Off: the test waits until you press Start.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = scheduled,
                    onCheckedChange = {
                        scheduled = it
                        if (it) {
                            // A scheduled test usually runs without the admin watching, so these two are switched on for you.
                            autoEnd = true
                            autoApproveLate = true
                            if (startMillis < System.currentTimeMillis() + 60_000L) startMillis = defaultStartMillis()
                        }
                    },
                )
            }
            if (scheduled) {
                StartTimeField(startMillis) { startMillis = it }
                if (!startOk) {
                    Text("Pick a time in the future.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                // The shared clock runs for the duration. With personal timers the test stays open for the late-join window as well.
                val lastMinutes = if (openType) openWindowMin else minutes + if (timerMode == "personal") (joinWindow.toIntOrNull() ?: 0) else 0
                val endMillis = startMillis + lastMinutes * 60_000L
                Text(
                    (if (openType) "Opens at ${formatWhen(startMillis)} and closes at ${formatWhen(endMillis)}."
                    else "Starts at ${formatWhen(startMillis)} and ends at ${formatWhen(endMillis)}" +
                        if (autoEnd) ", or sooner once everyone has submitted." else "."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!openType) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Let late joiners in automatically", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (autoApproveLate) "Anyone who joins after the start goes straight in. Their time still runs from the real start."
                            else "Anyone who joins after the start waits for you to allow them, so someone must be watching.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(checked = autoApproveLate, onCheckedChange = { autoApproveLate = it })
                }
                }
            }

            if (!openType) {
            Text("When does each player's time start?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = timerMode == "shared", onClick = { timerMode = "shared" }, label = { Text("Same clock for everyone") })
                FilterChip(selected = timerMode == "personal", onClick = { timerMode = "personal" }, label = { Text("Full time for each player") })
            }
            if (timerMode == "shared") {
                Text(
                    "The time starts counting for everyone the moment you start the test, whether or not they have joined. Someone who joins 5 minutes late has 5 minutes less.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "Every player gets the full time from when they begin, even if they join after you start. Players who ask to join after you start can be let in up to the number of minutes below, so the whole session lasts the duration plus this window.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    joinWindow, { joinWindow = it.filter(Char::isDigit).take(2) },
                    label = { Text("Late request window (minutes, 0 to 60)") }, singleLine = true,
                    isError = !windowOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            }
            Text("When do players see their score and position?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = scoreVisibility == "after_end", onClick = { scoreVisibility = "after_end" }, label = { Text("When the test ends") })
                FilterChip(selected = scoreVisibility == "immediate", onClick = { scoreVisibility = "immediate" }, label = { Text("Right after they finish") })
            }
            Text(
                if (scoreVisibility == "immediate") {
                    "Each player sees their score and position as soon as they submit or their time runs out. The position can still change while others finish. The correct answers stay hidden until the test ends, so early finishers cannot pass them on."
                } else {
                    "Scores, positions and corrections all appear when the test ends (when the time is up or you end it)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!openType) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("End when everyone has submitted", style = MaterialTheme.typography.titleMedium)
                    Text("The test ends by itself as soon as every player who was let in has submitted, instead of waiting for the time to run out. You can still end it earlier yourself.", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = autoEnd, onCheckedChange = { autoEnd = it })
            }
            }
            Text(
                (if (openType) "Nobody can start after the window closes. "
                else if (scheduled && autoApproveLate) "Players who join after the start are let in automatically. "
                else "Players who join after you start must send a request, and you decide in the Requests list. ") +
                    if (strictLeave) "Anyone who leaves the app during the test is closed out for good."
                    else "Anyone who leaves the app during the test is locked out until you let them back in.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strict mode (anti-malpractice)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (strictLeave) "A player who leaves the app, or goes offline for a minute, is closed out for good: answers so far are graded and they cannot return, even if you are offline. One phone can join only once."
                        else "Off: you decide whether a player who left may come back.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(checked = strictLeave, onCheckedChange = { strictLeave = it })
            }
            if (strictLeave) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("I may let people back in", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (strictReturn) "Someone who left can ask to come back. You see the request in Requests and decide, only while you are online. If you do not answer, their result stands. Good for a test you attend, when a network or phone problem can happen."
                            else "Off: nobody can come back after leaving, even if it was a network problem. Best for a public test you are not present at.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(checked = strictReturn, onCheckedChange = { strictReturn = it })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notify everyone", style = MaterialTheme.typography.titleMedium)
                    Text("Sends a phone notice with the code when you create the test and when you start it. Turn off for a private test.", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = announce, onCheckedChange = { announce = it })
            }

            Text("Questions from the bank", style = MaterialTheme.typography.titleMedium)
            Text("Type how many to draw at random from each subject. Only answer-checked questions are used.", style = MaterialTheme.typography.bodySmall)
            counts.forEach { sc ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${sc.subject} (${sc.count})", Modifier.weight(1f))
                    OutlinedTextField(
                        picks[sc.subject] ?: "", { picks[sc.subject] = it.filter(Char::isDigit).take(3) },
                        label = { Text("How many") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }

            Text("Your own questions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste questions, with or without options. The AI tidies them and, when a question has no options, writes four. It never adds questions you did not paste. You confirm every correct answer.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Import CSV / JSON file") }
                TextButton(onClick = { showGuide = true }) { Text("File format") }
            }
            Text(
                "You can paste for up to $MAX_TEST_SUBJECTS subjects. They all share the same time. Questions stay grouped by subject, in the order you add them.",
                style = MaterialTheme.typography.bodySmall,
            )
            val usedBlocks = pasteBlocks.filter { it.text.trim().length >= 5 }
            val subjectsNamed = usedBlocks.size <= 1 || usedBlocks.all { it.subject.isNotBlank() }
            pasteBlocks.forEachIndexed { bi, blk ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Subject ${bi + 1}", style = MaterialTheme.typography.titleSmall)
                            if (pasteBlocks.size > 1) TextButton(onClick = { pasteBlocks.removeAt(bi) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                        OutlinedTextField(
                            blk.subject, { pasteBlocks[bi] = blk.copy(subject = it.take(60)) },
                            label = { Text(if (pasteBlocks.size > 1) "Subject name" else "Subject (optional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            blk.text, { pasteBlocks[bi] = blk.copy(text = it.take(12000)) },
                            label = { Text("Paste ${blk.subject.ifBlank { "questions" }} questions here") },
                            minLines = 4, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (!subjectsNamed) Text("Give each subject a name before you tidy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (pasteBlocks.size < MAX_TEST_SUBJECTS) {
                OutlinedButton(onClick = { pasteBlocks.add(PasteBlock()) }) { Text("Add another subject (${pasteBlocks.size}/$MAX_TEST_SUBJECTS)") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !drafting && usedBlocks.isNotEmpty() && subjectsNamed,
                    onClick = {
                        scope.launch {
                            drafting = true; error = null
                            try {
                                var found = 0
                                // One AI call per subject, in the order shown. A failure keeps the blocks already tidied.
                                for ((bi, blk) in pasteBlocks.withIndex()) {
                                    if (blk.text.trim().length < 5) continue
                                    val r = LiveManager.adminDraft(blk.text, blk.subject.trim())
                                    r.items.forEach { it ->
                                        manual.add(
                                            ManualDraft(
                                                it.question, it.section, it.a, it.b, it.c, it.d, it.explanation,
                                                it.suggested_answer.firstOrNull()?.takeIf { ch -> ch in "abcd" },
                                                note = listOfNotNull(
                                                    if (!it.had_options) "The AI wrote the options. Check them." else null,
                                                    if (!it.confident) "The AI is not sure of the answer. Check it." else null,
                                                ).joinToString(" ").ifBlank { "AI suggests the answer shown. Check it." },
                                                subject = it.subject,
                                            )
                                        )
                                    }
                                    found += r.items.size
                                    if (r.items.isNotEmpty()) pasteBlocks[bi] = blk.copy(text = "")
                                }
                                if (found == 0) error = "The AI found no questions in that text."
                            } catch (e: Exception) {
                                error = e.friendlyMessage()
                            }
                            drafting = false
                        }
                    },
                ) { Text(if (drafting) "Tidying..." else if (usedBlocks.size > 1) "Tidy all with AI" else "Tidy with AI") }
                OutlinedButton(onClick = { manual.add(ManualDraft(subject = pasteBlocks.firstOrNull()?.subject.orEmpty())) }) { Text("Add blank question") }
            }

            manual.forEachIndexed { i, m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Question ${i + 1}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { manual.removeAt(i) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                        m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        OutlinedTextField(m.subject, { manual[i] = m.copy(subject = it.take(60)) }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.question, { manual[i] = m.copy(question = it) }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.a, { manual[i] = m.copy(a = it) }, label = { Text("A") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.b, { manual[i] = m.copy(b = it) }, label = { Text("B") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.c, { manual[i] = m.copy(c = it) }, label = { Text("C") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.d, { manual[i] = m.copy(d = it) }, label = { Text("D") }, modifier = Modifier.fillMaxWidth())
                        Text("Correct answer", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf('a', 'b', 'c', 'd').forEach { l ->
                                FilterChip(selected = m.answer == l, onClick = { manual[i] = m.copy(answer = l) }, label = { Text(l.uppercase()) })
                            }
                        }
                        OutlinedTextField(m.explanation, { manual[i] = m.copy(explanation = it) }, label = { Text("Explanation (shown after the test)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun AdminResultsScreen(id: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<AdminResults?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        while (true) {
            try { data = LiveManager.adminResults(id); error = null } catch (e: Exception) { error = e.friendlyMessage() }
            delay(4000)
        }
    }
    Scaffold(topBar = { BackBar(data?.title?.ifBlank { null } ?: "Results", onBack) }) { pad ->
        val d = data
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (d == null) {
                item { CircularProgressIndicator() }
            } else {
                item {
                    Text("Code ${d.code} · " + when (d.status) {
                        "waiting" -> "waiting to start"
                        "running" -> "running, ${formatTime(d.seconds_left)} left"
                        else -> "ended"
                    }, style = MaterialTheme.typography.titleMedium)
                    Text("${d.joined.size} joined: ${d.joined.joinToString(", ").ifBlank { "nobody yet" }}", style = MaterialTheme.typography.bodySmall)
                }
                item { Text("Scoreboard (${d.ranked.size} submitted)", style = MaterialTheme.typography.titleMedium) }
                items(d.ranked) { BoardRow(it) }
            }
        }
    }
}

/** Escapes one CSV cell per RFC 4180 (quotes the value if it holds a comma, quote or newline). */
private fun csvCell(s: String): String {
    val escaped = s.replace("\"", "\"\"")
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

// Last column, "action", is left blank on export; fill it in and re-import the same file to apply
// corrections. Accepted values: approve, remove, or a/b/c/d/e to set that as the correct option.
private val DISPUTED_CSV_HEADER = listOf(
    "id", "status", "subject", "question", "section", "image",
    "a", "b", "c", "d", "e", "stored_answer", "ai_answer", "ai_explanation", "action",
)

private fun DisputedItem.toCsvRow(): String = listOf(
    id, review_status, subject, question, section, image,
    a, b, c, d, e, answer, ai_answer ?: "", ai_explanation ?: "", "",
).joinToString(",") { csvCell(it) }

private fun buildDisputedCsv(items: List<DisputedItem>): String = buildString {
    append(DISPUTED_CSV_HEADER.joinToString(",") { csvCell(it) }).append("\r\n")
    for (it in items) append(it.toCsvRow()).append("\r\n")
}

private val IMPORT_ACTIONS = setOf("approve", "remove", "a", "b", "c", "d", "e")

/** One correction read from an uploaded file: do this to the question with this id. */
private data class ImportRow(val id: String, val action: String)

private data class ImportParseResult(val rows: List<ImportRow>, val skippedNoId: Int, val skippedNoAction: Int, val skippedBadAction: Int)

/** Minimal RFC 4180 reader: handles quoted fields, escaped quotes, and commas/newlines inside quotes. */
private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var field = StringBuilder()
    var row = mutableListOf<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field = StringBuilder() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field = StringBuilder(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
    return rows
}

@Serializable
private data class ImportRowJson(val id: String = "", val action: String = "")

@Serializable
private data class ImportWrapperJson(val items: List<ImportRowJson> = emptyList())

private fun parseImportRows(text: String): ImportParseResult {
    var noId = 0; var noAction = 0; var badAction = 0
    val out = mutableListOf<ImportRow>()
    fun consider(id: String, action: String) {
        val cid = id.trim()
        val act = action.trim().lowercase()
        when {
            cid.isEmpty() -> noId++
            act.isEmpty() -> noAction++
            act !in IMPORT_ACTIONS -> badAction++
            else -> out.add(ImportRow(cid, act))
        }
    }
    val trimmed = text.trimStart()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        val list = runCatching { LiveManager.json.decodeFromString<List<ImportRowJson>>(text) }
            .getOrElse { LiveManager.json.decodeFromString<ImportWrapperJson>(text).items }
        for (r in list) consider(r.id, r.action)
    } else {
        val table = parseCsv(text)
        if (table.isEmpty()) return ImportParseResult(emptyList(), 0, 0, 0)
        val header = table[0].map { it.trim().lowercase() }
        val idIdx = header.indexOf("id")
        val actionIdx = header.indexOf("action")
        if (idIdx < 0 || actionIdx < 0) {
            throw IllegalArgumentException("The file needs \"id\" and \"action\" columns.")
        }
        for (r in table.drop(1)) {
            if (r.size <= 1 && r.getOrNull(0).isNullOrBlank()) continue
            consider(r.getOrNull(idIdx) ?: "", r.getOrNull(actionIdx) ?: "")
        }
    }
    return ImportParseResult(out, noId, noAction, badAction)
}

@Composable
fun AdminDisputedScreen(onBack: () -> Unit) {
    val rows = remember { mutableStateListOf<DisputedItem>() }
    var offset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var removing by remember { mutableStateOf<DisputedItem?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var exportMenu by remember { mutableStateOf(false) }
    var exportingAll by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ImportParseResult?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0 to 0) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reports = remember { mutableStateMapOf<String, List<ReportInfo>>() }
    var counts by remember { mutableStateOf<QueueCounts?>(null) }

    suspend fun loadCounts() { Reporter.adminCounts()?.let { counts = it } }
    fun refreshCounts() { scope.launch { loadCounts() } }

    fun refreshList() {
        rows.clear(); reports.clear(); offset = 0; more = true; selected.clear(); reload++
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { it.write(csv) }
                }
                error = null
            } catch (e: Exception) {
                error = "Couldn't save the file: ${e.message}"
            }
        }
    }

    fun exportCsv(items: List<DisputedItem>) {
        if (items.isEmpty()) { error = "Nothing to export."; return }
        pendingCsv = buildDisputedCsv(items)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        saveLauncher.launch("pastq_review_$stamp.csv")
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: throw IllegalArgumentException("Couldn't read that file.")
                importPreview = withContext(Dispatchers.IO) { parseImportRows(text) }
            } catch (e: Exception) {
                error = "Couldn't read the file: ${e.message ?: "unknown error"}"
            }
        }
    }

    LaunchedEffect(reload) {
        loading = true
        try {
            val r = LiveManager.adminDisputed(offset)
            rows.addAll(r.items)
            reports.putAll(Reporter.adminReports(r.items.map { it.id }))
            offset += r.items.size
            more = r.items.size >= 20
            error = null
        } catch (e: Exception) {
            error = e.friendlyMessage()
        }
        loading = false
        loadCounts()
    }

    removing?.let { q ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this question?") },
            text = { Text("It is deleted for everyone and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = q; removing = null
                    scope.launch {
                        try { LiveManager.adminRemove(target.id); rows.remove(target); selected.remove(target.id); error = null; refreshCounts() }
                        catch (e: Exception) { error = e.friendlyMessage() }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    importPreview?.let { p ->
        val approve = p.rows.count { it.action == "approve" }
        val remove = p.rows.count { it.action == "remove" }
        val rekey = p.rows.size - approve - remove
        val skipped = p.skippedNoId + p.skippedNoAction + p.skippedBadAction
        AlertDialog(
            onDismissRequest = { if (!importBusy) importPreview = null },
            title = { Text("Apply corrections?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (importBusy) {
                        Text("Applying ${importProgress.first} of ${importProgress.second}…")
                    } else {
                        if (p.rows.isEmpty()) {
                            Text("No usable rows found. Each row needs an id and an action (approve, remove, or a/b/c/d/e).")
                        } else {
                            Text("$approve to approve as-is, $rekey to re-key, $remove to remove. ${p.rows.size} total.")
                        }
                        if (skipped > 0) {
                            Text(
                                "$skipped row(s) skipped (${p.skippedNoId} no id, ${p.skippedNoAction} no action, ${p.skippedBadAction} unrecognized action).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !importBusy && p.rows.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            importBusy = true
                            var ok = 0
                            val failures = mutableListOf<String>()
                            p.rows.forEachIndexed { i, row ->
                                importProgress = (i + 1) to p.rows.size
                                try {
                                    when (row.action) {
                                        "approve" -> LiveManager.adminApprove(row.id)
                                        "remove" -> LiveManager.adminRemove(row.id)
                                        else -> LiveManager.adminSetKey(row.id, row.action[0])
                                    }
                                    ok++
                                } catch (e: Exception) {
                                    failures.add("${row.id}: ${e.friendlyMessage()}")
                                }
                            }
                            importBusy = false
                            importPreview = null
                            importResult = buildString {
                                append("$ok of ${p.rows.size} applied.")
                                if (failures.isNotEmpty()) {
                                    append("\n\nFailed:\n")
                                    append(failures.take(10).joinToString("\n"))
                                    if (failures.size > 10) append("\n…and ${failures.size - 10} more.")
                                }
                            }
                            refreshList()
                        }
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(enabled = !importBusy, onClick = { importPreview = null }) { Text("Cancel") } },
        )
    }

    importResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import finished") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } },
        )
    }

    Scaffold(
        topBar = {
            BackBar("Review answer keys", onBack) {
                Box {
                    TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = !exportingAll) { Text("Import") }
                }
                Box {
                    TextButton(onClick = { exportMenu = true }, enabled = !exportingAll) {
                        if (exportingAll) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Export")
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export selected (${selected.size})") },
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                exportMenu = false
                                exportCsv(rows.filter { it.id in selected })
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export all disputed/faulty") },
                            onClick = {
                                exportMenu = false
                                scope.launch {
                                    exportingAll = true
                                    try {
                                        val all = mutableListOf<DisputedItem>()
                                        var off = 0
                                        while (true) {
                                            val r = LiveManager.adminDisputed(off)
                                            if (r.items.isEmpty()) break
                                            all.addAll(r.items)
                                            off += r.items.size
                                            if (r.items.size < 20) break
                                        }
                                        exportCsv(all)
                                        error = null
                                    } catch (e: Exception) {
                                        error = e.friendlyMessage()
                                    }
                                    exportingAll = false
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            counts?.let { c ->
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (c.left == 0) "All clear: nothing left to review" else "${c.left} question(s) left to review",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("${c.disputed} disputed · ${c.unverifiable} could not be verified", style = MaterialTheme.typography.bodySmall)
                            Text("${c.unchecked} not yet checked by the AI", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Text(
                    "Questions the AI disputed or could not verify. Approve to keep the stored answer, pick the correct option to fix it, or remove the question. Approving or fixing counts it as confirmed. Tick questions to export a bulk selection, or use Export > Export all to save everything in one CSV file. Fill in the blank \"action\" column (approve, remove, or a/b/c/d/e) and use Import to apply your corrections in one go.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (rows.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = {
                            if (selected.size == rows.size) selected.clear() else { selected.clear(); selected.addAll(rows.map { it.id }) }
                        }) { Text(if (selected.size == rows.size) "Clear selection" else "Select all loaded (${rows.size})") }
                        if (selected.isNotEmpty()) Text("${selected.size} selected", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(rows, key = { it.id }) { q ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = q.id in selected,
                                onCheckedChange = { checked -> if (checked) selected.add(q.id) else selected.remove(q.id) },
                            )
                            Text("${q.subject} · ${q.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (q.review_status == "unverifiable") "Unverifiable" else "Disputed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (q.section.isNotBlank()) HtmlText(q.section, style = MaterialTheme.typography.bodySmall)
                        HtmlText(q.question, style = MaterialTheme.typography.titleSmall)
                        QuestionImage(q.image)
                        listOf('a' to q.a, 'b' to q.b, 'c' to q.c, 'd' to q.d, 'e' to q.e).forEach { (l, text) ->
                            if (text.isBlank()) return@forEach
                            val tags = listOfNotNull(
                                if (q.answer == l.toString()) "stored key" else null,
                                if (q.ai_answer == l.toString()) "AI says" else null,
                            ).joinToString(", ")
                            HtmlText(text, prefix = "${l.uppercaseChar()}. ", suffix = if (tags.isNotEmpty()) "   [$tags]" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                        q.ai_explanation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        reports[q.id]?.takeIf { it.isNotEmpty() }?.let { list ->
                            Text("Reported by players (${list.size}):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            list.forEach { rep ->
                                Text(
                                    "• ${Reporter.label(rep.reason)}" + if (rep.note.isNotBlank()) ": ${rep.note}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text("Correct option (or approve / remove):", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf('a' to q.a, 'b' to q.b, 'c' to q.c, 'd' to q.d, 'e' to q.e)
                                .filter { it.second.isNotBlank() }
                                .forEach { (l, _) ->
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            try { LiveManager.adminSetKey(q.id, l); rows.remove(q); selected.remove(q.id); error = null; refreshCounts() }
                                            catch (e: Exception) { error = e.friendlyMessage() }
                                        }
                                    }) { Text(l.uppercase()) }
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = {
                                scope.launch {
                                    try { LiveManager.adminApprove(q.id); rows.remove(q); selected.remove(q.id); error = null; refreshCounts() }
                                    catch (e: Exception) { error = e.friendlyMessage() }
                                }
                            }, modifier = Modifier.weight(1f)) { Text("Approve as-is") }
                            OutlinedButton(
                                onClick = { removing = q },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                when {
                    loading -> CircularProgressIndicator()
                    more -> OutlinedButton(onClick = { reload++ }) { Text("Load more") }
                    rows.isEmpty() -> Text("Nothing to review.")
                }
            }
        }
    }
}

/** "1 day 2 hours 30 min" style text for an open window given in minutes. */
private fun formatOpenWindow(totalMin: Int): String {
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return listOfNotNull(
        if (d > 0) "$d day${if (d > 1) "s" else ""}" else null,
        if (h > 0) "$h hour${if (h > 1) "s" else ""}" else null,
        if (m > 0) "$m min" else null,
    ).joinToString(" ").ifBlank { "0 min" }
}
