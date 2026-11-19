package com.pastq.cbt.data

import android.content.Context
import com.pastq.cbt.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
data class ReportInfo(val reason: String = "", val note: String = "", val created_at: String = "")

/** How many questions still wait for the admin to review. */
@Serializable
data class QueueCounts(val disputed: Int = 0, val unverifiable: Int = 0, val unchecked: Int = 0) {
    val left: Int get() = disputed + unverifiable
}

@Serializable
private data class AdminReportsReply(val reports: Map<String, List<ReportInfo>> = emptyMap())

/** Lets players flag a faulty question (wrong key, wrong option, typo...) and lets the admin read the reasons. */
object Reporter {
    /** Reason code (stored on the server) to the label shown to the player. */
    val reasons = listOf(
        "wrong_key" to "Wrong answer key (the marked correct answer is wrong)",
        "wrong_option" to "An option is wrong, missing or repeated",
        "question_error" to "Typing error or unclear question",
        "missing_media" to "Picture, passage or table is missing",
        "duplicate" to "Duplicate question",
        "other" to "Something else",
    )

    fun label(code: String): String = reasons.firstOrNull { it.first == code }?.second ?: code

    /** Random id kept on this phone so one person's repeat reports are not counted twice. */
    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("report", Context.MODE_PRIVATE)
        return prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device", it).apply()
        }
    }

    /** Returns true when the question was moved into the admin review queue. */
    suspend fun send(context: Context, questionId: String, reason: String, note: String): Result<Boolean> = runCatching {
        val key = BuildConfig.SUPABASE_KEY
        val body = buildJsonObject {
            put("action", "report")
            put("id", questionId)
            put("reason", reason)
            put("note", note)
            put("device", deviceId(context))
        }
        val r = CloudApi.service.reportCall(key, "Bearer $key", null, body)
        r["flagged"]?.jsonPrimitive?.booleanOrNull ?: false
    }

    /** Open reports for these question ids (admin only). Returns nothing if the admin is signed out or the call fails. */
    suspend fun adminReports(ids: List<String>): Map<String, List<ReportInfo>> {
        val token = LiveManager.admin.value ?: return emptyMap()
        if (ids.isEmpty()) return emptyMap()
        return runCatching {
            val key = BuildConfig.SUPABASE_KEY
            val body = buildJsonObject {
                put("action", "admin_reports")
                put("ids", kotlinx.serialization.json.JsonArray(ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            val raw = CloudApi.service.reportCall(key, "Bearer $key", token, body)
            LiveManager.json.decodeFromJsonElement<AdminReportsReply>(raw).reports
        }.getOrDefault(emptyMap())
    }

    /** Disputed / unverifiable / unchecked question totals (admin only). Null if signed out or the call fails. */
    suspend fun adminCounts(): QueueCounts? {
        val token = LiveManager.admin.value ?: return null
        return runCatching {
            val key = BuildConfig.SUPABASE_KEY
            val body = buildJsonObject { put("action", "admin_counts") }
            val raw = CloudApi.service.reportCall(key, "Bearer $key", token, body)
            LiveManager.json.decodeFromJsonElement<QueueCounts>(raw)
        }.getOrNull()
    }
}
