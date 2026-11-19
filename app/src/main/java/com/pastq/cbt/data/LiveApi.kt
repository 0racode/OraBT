package com.pastq.cbt.data

import android.content.Context
import android.content.SharedPreferences
import com.pastq.cbt.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** One question in a live session. Players get it without [answer]; the review after the test includes it. */
@Serializable
data class LiveItem(
    val pos: Int = 0,
    val qid: String? = null,
    val subject: String = "",
    val section: String = "",
    val question: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val e: String = "",
    val answer: String = "",
    val explanation: String = "",
    val image: String = "",
    val picked: String? = null,
) {
    fun optionText(letter: Char): String = when (letter) {
        'a' -> a
        'b' -> b
        'c' -> c
        'd' -> d
        else -> e
    }

    /** The same question shaped like a bank question, so the normal question card can show it. */
    fun asQuestion() = QuestionEntity(
        id = qid ?: "live-$pos", subject = subject, question = question,
        a = a, b = b, c = c, d = d, e = e, answer = answer, section = section, image = image,
    )
}

@Serializable
data class LiveBoardRow(
    val rank: Int = 0,
    val name: String = "",
    val correct: Int = 0,
    val total: Int = 0,
    val seconds: Int = 0,
    val you: Boolean = false,
)

@Serializable
data class LiveState(
    val title: String = "",
    val status: String = "",
    val minutes: Int = 0,
    val seconds_left: Int = 0,
    val players: Int = 0,
    val submitted: Boolean = false,
    val name: String = "",
    val questions: List<LiveItem> = emptyList(),
    val timer_mode: String = "shared",
    val score_visibility: String = "after_end",
    /** "approved", "pending" (late joiner waiting for the admin) or "denied". */
    val join_status: String = "approved",
    /** True after the player left the app during the test; the admin must let them back in. */
    val locked: Boolean = false,
    val rejoin_requested: Boolean = false,
    /** Answers saved on the server (kept when the player left earlier), position -> letter. */
    val saved_answers: Map<String, String> = emptyMap(),
)

@Serializable
data class LiveResults(
    val ready: Boolean = false,
    /** False while the session is still running: score and position can still change and corrections are hidden. */
    val final: Boolean = true,
    val status: String = "",
    val seconds_left: Int = 0,
    val title: String = "",
    val name: String = "",
    val submitted: Boolean = false,
    val correct: Int = 0,
    val total: Int = 0,
    val seconds: Int = 0,
    val rank: Int? = null,
    val participants: Int = 0,
    val joined: Int = 0,
    val board: List<LiveBoardRow> = emptyList(),
    val review: List<LiveItem> = emptyList(),
)

@Serializable
data class JoinReply(
    val token: String = "", val name: String = "", val code: String = "", val title: String = "",
    val join_status: String = "approved",
)

/** One person waiting for the admin: a late joiner ("join") or someone who left the app and wants back in ("rejoin"). */
@Serializable
data class JoinRequest(val player_id: String = "", val name: String = "", val kind: String = "join")

@Serializable
data class AdminRequests(val status: String = "", val requests: List<JoinRequest> = emptyList())

@Serializable
data class AdminToken(val token: String = "")

@Serializable
data class AdminSession(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val minutes: Int = 0,
    val status: String = "",
    val subjects: String = "",
    val players: Int = 0,
    val submitted: Int = 0,
    val questions: Int = 0,
    val seconds_left: Int = 0,
    /** Late-join and come-back requests waiting for the admin. */
    val requests: Int = 0,
    val auto_end: Boolean = false,
)

@Serializable
data class AdminSessions(val sessions: List<AdminSession> = emptyList())

@Serializable
data class AdminResults(
    val title: String = "",
    val code: String = "",
    val status: String = "",
    val seconds_left: Int = 0,
    val joined: List<String> = emptyList(),
    val ranked: List<LiveBoardRow> = emptyList(),
)

@Serializable
data class CreatedSession(val id: String = "", val code: String = "", val title: String = "", val questions: Int = 0)

@Serializable
data class DraftItem(
    val question: String = "",
    val section: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val suggested_answer: String = "",
    val explanation: String = "",
    val had_options: Boolean = false,
    val confident: Boolean = false,
    val subject: String = "",
)

@Serializable
data class DraftReply(val items: List<DraftItem> = emptyList())

@Serializable
data class DisputedItem(
    val id: String = "",
    val subject: String = "",
    val question: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val e: String = "",
    val answer: String = "",
    val section: String = "",
    val image: String = "",
    val ai_answer: String? = null,
    val ai_explanation: String? = null,
    val review_status: String = "disputed",
)

@Serializable
data class DisputedReply(val items: List<DisputedItem> = emptyList())

/** Live sessions: talks to the pastq-live server function. The admin sign-in lives only in memory. */
object LiveManager {
    data class Joined(val code: String, val token: String, val name: String, val title: String)

    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private lateinit var prefs: SharedPreferences

    private val _joined = MutableStateFlow<Joined?>(null)
    val joined: StateFlow<Joined?> = _joined

    private val _admin = MutableStateFlow<String?>(null)
    val admin: StateFlow<String?> = _admin

    /** Answers picked so far in the running test (position -> letter). Kept while the app is open. */
    val answers = MutableStateFlow<Map<Int, Char>>(emptyMap())

    /** Session code from a tapped notification; the join screen fills it in. */
    val pendingCode = MutableStateFlow<String?>(null)

    suspend fun registerToken(token: String) {
        runCatching { val _r: JsonObject = request("register_token") { put("token", token) } }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("live", Context.MODE_PRIVATE)
        val code = prefs.getString("code", null)
        val token = prefs.getString("token", null)
        if (code != null && token != null) {
            _joined.value = Joined(code, token, prefs.getString("name", "") ?: "", prefs.getString("title", "") ?: "")
        }
    }

    fun forget() {
        prefs.edit().clear().apply()
        _joined.value = null
        answers.value = emptyMap()
    }

    fun pick(pos: Int, letter: Char) = answers.update { it + (pos to letter) }

    /** Puts back answers the server kept from before the player left, without overwriting newer local picks. */
    fun restoreAnswers(saved: Map<String, String>) {
        if (saved.isEmpty()) return
        answers.update { cur ->
            val back = saved.mapNotNull { (k, v) ->
                val pos = k.toIntOrNull(); val ch = v.firstOrNull()
                if (pos != null && ch != null) pos to ch else null
            }.toMap()
            back + cur
        }
    }

    fun signOutAdmin() { _admin.value = null }

    private suspend fun call(body: JsonObject, admin: Boolean): JsonObject {
        val key = BuildConfig.SUPABASE_KEY
        try {
            return CloudApi.service.live(key, "Bearer $key", if (admin) _admin.value else null, body)
        } catch (e: retrofit2.HttpException) {
            if (admin && e.code() == 401) _admin.value = null
            throw e
        }
    }

    private suspend inline fun <reified T> request(
        action: String,
        admin: Boolean = false,
        build: JsonObjectBuilder.() -> Unit = {},
    ): T {
        val body = buildJsonObject { put("action", action); build() }
        return json.decodeFromJsonElement(call(body, admin))
    }

    // ----- players -----
    /** [autoName] is true for signed-in players: the server adds a number if someone in the session already has that name. */
    suspend fun join(code: String, name: String, autoName: Boolean = false): Result<Joined> = runCatching {
        val r: JoinReply = request("join") { put("code", code.trim()); put("name", name.trim()); put("auto_name", autoName) }
        answers.value = emptyMap()
        val j = Joined(r.code, r.token, r.name, r.title)
        prefs.edit().putString("code", j.code).putString("token", j.token).putString("name", j.name).putString("title", j.title).apply()
        _joined.value = j
        j
    }

    suspend fun state(): LiveState {
        val j = _joined.value ?: throw IllegalStateException("Join a session first.")
        return request("state") { put("code", j.code); put("token", j.token) }
    }

    suspend fun submit(): Result<Unit> = runCatching {
        val j = _joined.value ?: throw IllegalStateException("Join a session first.")
        val picked = answers.value
        val _r: JsonObject = request("submit") {
            put("code", j.code); put("token", j.token)
            put("answers", buildJsonObject { picked.forEach { (pos, letter) -> put(pos.toString(), letter.toString()) } })
        }
    }

    /** Tells the server the player left the app during the test. The answers so far are kept; the admin decides about a return. */
    suspend fun leave(): Boolean {
        val j = _joined.value ?: return false
        val picked = answers.value
        repeat(3) {
            val ok = runCatching {
                val _r: JsonObject = request("leave") {
                    put("code", j.code); put("token", j.token)
                    put("answers", buildJsonObject { picked.forEach { (pos, letter) -> put(pos.toString(), letter.toString()) } })
                }
            }.isSuccess
            if (ok) return true
            kotlinx.coroutines.delay(1500)
        }
        return false
    }

    /** Asks the admin to let a locked-out player back in. */
    suspend fun rejoinRequest(): Result<Unit> = runCatching {
        val j = _joined.value ?: throw IllegalStateException("Join a session first.")
        val _r: JsonObject = request("rejoin_request") { put("code", j.code); put("token", j.token) }
    }

    suspend fun results(): LiveResults {
        val j = _joined.value ?: throw IllegalStateException("Join a session first.")
        return request("results") { put("code", j.code); put("token", j.token) }
    }

    // ----- admin -----
    suspend fun adminLogin(login: String, password: String): Result<Unit> = runCatching {
        val r: AdminToken = request("admin_login") { put("login", login); put("password", password) }
        _admin.value = r.token
    }

    suspend fun adminList(): AdminSessions = request("admin_list", true)

    suspend fun adminStart(id: String): JsonObject = request("admin_start", true) { put("id", id) }
    suspend fun adminEnd(id: String): JsonObject = request("admin_end", true) { put("id", id) }
    suspend fun adminDelete(id: String): JsonObject = request("admin_delete", true) { put("id", id) }
    suspend fun adminResults(id: String): AdminResults = request("admin_results", true) { put("id", id) }
    suspend fun adminRequests(id: String): AdminRequests = request("admin_requests", true) { put("id", id) }
    suspend fun adminDecide(id: String, playerId: String, allow: Boolean): JsonObject =
        request("admin_decide", true) { put("id", id); put("player_id", playerId); put("allow", allow) }

    suspend fun adminCreate(
        title: String,
        minutes: Int,
        picks: Map<String, Int>,
        manual: List<DraftItem>,
        answers: List<Char>,
        announce: Boolean = true,
        /** "shared": one clock from the admin's start. "personal": every player gets the full time from when they begin. */
        timerMode: String = "shared",
        /** Personal timer only: players may join up to this many minutes after the admin starts the test. */
        joinWindowMin: Int = 10,
        /** "after_end": scores appear when the test ends. "immediate": right after each player finishes. */
        scoreVisibility: String = "after_end",
        /** Optional custom join code (4 to 12 letters or digits). Empty means a random code. */
        code: String = "",
        /** End the test automatically as soon as every player has submitted. */
        autoEnd: Boolean = false,
    ): CreatedSession = request("admin_create", true) {
        put("title", title)
        put("code", code)
        put("auto_end", autoEnd)
        put("announce", announce)
        put("minutes", minutes)
        put("timer_mode", timerMode)
        put("join_window_min", joinWindowMin)
        put("score_visibility", scoreVisibility)
        put("picks", JsonArray(picks.filter { it.value > 0 }.map { (s, n) ->
            buildJsonObject { put("subject", s); put("count", n) }
        }))
        put("manual", JsonArray(manual.mapIndexed { i, m ->
            buildJsonObject {
                put("subject", m.subject.ifBlank { "Custom" })
                put("question", m.question); put("section", m.section)
                put("a", m.a); put("b", m.b); put("c", m.c); put("d", m.d)
                put("answer", answers[i].toString()); put("explanation", m.explanation)
            }
        }))
    }

    suspend fun adminDraft(text: String, subject: String): DraftReply =
        request("admin_ai_draft", true) { put("text", text); put("subject", subject) }

    suspend fun adminDisputed(offset: Int): DisputedReply = request("admin_disputed", true) { put("offset", offset) }

    suspend fun adminSetKey(id: String, answer: Char): JsonObject =
        request("admin_set_key", true) { put("id", id); put("answer", answer.toString()) }

    /** Confirms the stored answer as-is, without changing it. */
    suspend fun adminApprove(id: String): JsonObject = request("admin_approve", true) { put("id", id) }

    /** Permanently removes a bad question from the bank. */
    suspend fun adminRemove(id: String): JsonObject = request("admin_remove", true) { put("id", id) }
}
