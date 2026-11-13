package com.pastq.cbt.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable

/** Lives as long as the app process, for work that must finish even after a screen is closed. */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Serializable
data class AuthRequest(
    val action: String,
    val email: String? = null,
    val password: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val old_password: String? = null,
    val new_password: String? = null,
)

@Serializable
data class Profile(val email: String = "", val first_name: String = "", val last_name: String = "")

@Serializable
data class AuthReply(
    val token: String? = null,
    val profile: Profile? = null,
    val ok: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class LeaderboardRequest(val period: String, val lim: Int = 50)

@Serializable
data class LeaderRow(
    val rank: Int = 0,
    val name: String = "",
    val pct: Double = 0.0,
    val correct: Int = 0,
    val total: Int = 0,
    val mode: String = "",
    val seconds: Int = 0,
)

@Serializable
data class AnswerItem(val id: String, val picked: String? = null)

@Serializable
data class SubmitRequest(val mode: String, val subjects: String, val seconds: Int, val answers: List<AnswerItem>)

@Serializable
data class Ranks(val day: Int? = null, val week: Int? = null, val month: Int? = null)

@Serializable
data class SubmitReply(
    val eligible: Boolean = false,
    val correct: Int = 0,
    val total: Int = 0,
    val pct: Double = 0.0,
    val ranks: Ranks? = null,
    val message: String? = null,
    val error: String? = null,
)

/** What happened to the score after an exam, as shown on the result screen. */
sealed interface ScoreStatus {
    data object Idle : ScoreStatus
    data object SignedOut : ScoreStatus
    data object Submitting : ScoreStatus
    data class TooShort(val count: Int) : ScoreStatus
    data class Done(val reply: SubmitReply) : ScoreStatus
    data class Failed(val message: String) : ScoreStatus
}

/** Turns a network or server error into a sentence a student can read. */
fun Throwable.friendlyMessage(): String = when (this) {
    is retrofit2.HttpException -> {
        val raw = try { response()?.errorBody()?.string().orEmpty() } catch (e: Exception) { "" }
        Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1) ?: "Server error ${code()}."
    }
    is java.io.IOException -> "Can't reach the server. Check your internet connection."
    else -> message ?: "Something went wrong."
}
