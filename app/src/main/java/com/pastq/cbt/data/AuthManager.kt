package com.pastq.cbt.data

import android.content.Context
import android.content.SharedPreferences
import com.pastq.cbt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The optional PastQ account. Signed-out use of the app is always allowed. */
object AuthManager {
    data class Account(val email: String, val firstName: String, val lastName: String) {
        val displayName: String
            get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { email }
    }

    private lateinit var prefs: SharedPreferences
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    private val apiKey get() = BuildConfig.SUPABASE_KEY
    val token: String? get() = prefs.getString("token", null)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)
        if (token != null && email != null) {
            _account.value = Account(email, prefs.getString("first", "") ?: "", prefs.getString("last", "") ?: "")
            AppScope.launch { refresh() }
        }
    }

    private suspend fun call(req: AuthRequest, signedIn: Boolean = true): AuthReply {
        val bearer = if (signedIn) token ?: apiKey else apiKey
        return CloudApi.service.account(apiKey, "Bearer $bearer", req)
    }

    private fun save(newToken: String?, p: Profile) {
        prefs.edit().apply {
            if (newToken != null) putString("token", newToken)
            putString("email", p.email)
            putString("first", p.first_name)
            putString("last", p.last_name)
        }.apply()
        _account.value = Account(p.email, p.first_name, p.last_name)
    }

    private fun clear() {
        prefs.edit().clear().apply()
        _account.value = null
    }

    /** Re-reads the profile from the server; signs out locally if the session has expired. */
    private suspend fun refresh() {
        try {
            call(AuthRequest("me"))
                .profile?.let { save(null, it) }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) clear()
        } catch (e: Exception) {
            // Offline: keep the cached profile.
        }
    }

    /** Each of these returns null on success, or a message to show the user. */
    suspend fun signUp(email: String, password: String, first: String, last: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = call(AuthRequest("signup", email, password, first, last), signedIn = false)
            val p = r.profile
            if (r.token == null || p == null) r.error ?: "Could not create the account." else { save(r.token, p); null }
        } catch (e: Exception) { e.friendlyMessage() }
    }

    suspend fun signIn(email: String, password: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = call(AuthRequest("login", email, password), signedIn = false)
            val p = r.profile
            if (r.token == null || p == null) r.error ?: "Could not log in." else { save(r.token, p); null }
        } catch (e: Exception) { e.friendlyMessage() }
    }

    suspend fun updateProfile(first: String, last: String): String? = withContext(Dispatchers.IO) {
        try {
            val p = call(AuthRequest("update_profile", first_name = first, last_name = last)).profile
            if (p == null) "Could not save your profile." else { save(null, p); null }
        } catch (e: Exception) { e.friendlyMessage() }
    }

    suspend fun changePassword(old: String, new: String): String? = withContext(Dispatchers.IO) {
        try {
            call(AuthRequest("change_password", old_password = old, new_password = new)).error
        } catch (e: Exception) { e.friendlyMessage() }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try { call(AuthRequest("logout")) } catch (e: Exception) { /* best effort */ }
        clear()
    }

    suspend fun deleteAccount(): String? = withContext(Dispatchers.IO) {
        try {
            call(AuthRequest("delete_account"))
            clear()
            null
        } catch (e: Exception) { e.friendlyMessage() }
    }

    /** Called when the server says the session is no longer valid. */
    fun sessionExpired() = clear()
}
