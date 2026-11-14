package com.pastq.cbt.data

import com.pastq.cbt.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ExplainRequest(val id: String)

@Serializable
data class IdOnly(val id: String)

@Serializable
data class ExplainReply(
    val source: String = "",
    val explanation: String = "",
    val agrees: Boolean? = null,
    val ai_answer: String? = null,
    val key_answer: String? = null,
    val status: String? = null,
    val error: String? = null,
)

/**
 * Asks the server to explain one question from the shared question bank.
 * Only the question's id is sent, so the AI can never be asked about anything outside the bank.
 */
object Explainer {
    private val cache = HashMap<String, ExplainReply>()

    fun cached(id: String): ExplainReply? = cache[id]

    suspend fun fetch(id: String): Result<ExplainReply> = withContext(Dispatchers.IO) {
        cache[id]?.let { return@withContext Result.success(it) }
        try {
            val key = BuildConfig.SUPABASE_KEY
            val reply = CloudApi.service.explain(key, "Bearer $key", ExplainRequest(id))
            if (reply.explanation.isBlank()) {
                Result.failure(IllegalStateException(reply.error ?: "No explanation available."))
            } else {
                cache[id] = reply
                Result.success(reply)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.friendlyMessage()))
        }
    }
}
