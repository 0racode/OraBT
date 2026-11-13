package com.pastq.cbt.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class Repository(private val context: Context, private val db: AppDatabase) {
    private val qDao = db.questions()
    private val rDao = db.results()

    val subjectCounts: Flow<List<SubjectCount>> = qDao.subjectCounts()
    val results: Flow<List<ExamResultEntity>> = rDao.all()

    /** Imports the bundled questions on first launch. Safe to call repeatedly. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (qDao.count() > 0) return@withContext
        val text = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        val items = Json { ignoreUnknownKeys = true }.decodeFromString<List<QuestionEntity>>(text)
        items.chunked(500).forEach { qDao.insertAll(it) }
    }

    suspend fun types(subject: String) = qDao.types(subject)
    suspend fun years(subject: String) = qDao.years(subject)
    suspend fun practice(subject: String, type: String, year: String) = qDao.practice(subject, type, year)
    suspend fun random(subject: String, type: String, limit: Int) = qDao.random(subject, type, limit)
    suspend fun saveResult(result: ExamResultEntity) = rDao.insert(result)

    private suspend fun fetchBatch(key: String, subjectName: String) {
        val resp = AlocApi.service.many(com.pastq.cbt.BuildConfig.ALOC_TOKEN, key)
        val items = resp.data.mapNotNull { q ->
            val ans = q.answer.trim().lowercase().take(1)
            if (q.question.isBlank() || ans !in listOf("a", "b", "c", "d")) return@mapNotNull null
            if (q.image.isNotBlank()) return@mapNotNull null
            QuestionEntity(
                id = "$key-${q.id}", subject = subjectName,
                question = q.question.trim(),
                a = q.option.a.trim(), b = q.option.b.trim(), c = q.option.c.trim(), d = q.option.d.trim(),
                answer = ans, section = q.section.trim(), solution = q.solution.trim(),
                type = q.examtype.trim().lowercase(), year = q.examyear.trim(),
            )
        }
        qDao.insertAll(items)
    }

    /**
     * Copies the whole shared question library from the cloud (Supabase) onto this device.
     * Uses no ALOC API calls. Returns the number of questions that were new to this device.
     */
    suspend fun syncFromCloud(onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val before = qDao.count()
        val key = com.pastq.cbt.BuildConfig.SUPABASE_KEY
        var offset = 0
        val pageSize = 1000
        while (true) {
            val page = CloudApi.service.page(key, "Bearer $key", limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            qDao.insertAll(page)
            offset += page.size
            onProgress(offset)
            if (page.size < pageSize) break
        }
        qDao.count() - before
    }

    /**
     * Asks the cloud function to fetch more questions from ALOC for [subjectName] and save them
     * to the shared library (the ALOC token stays on the server). Max 5 ALOC calls per request.
     * Throws with a readable message on failure or when the daily limit is hit.
     */
    suspend fun cloudFetch(subjectName: String, calls: Int): SyncReply = withContext(Dispatchers.IO) {
        val key = com.pastq.cbt.BuildConfig.SUPABASE_KEY
        val subjectKey = Subjects.keyByName[subjectName] ?: error("Unknown subject")
        try {
            CloudApi.service.syncAloc(key, "Bearer $key", SyncRequest(subjectKey, calls))
        } catch (e: retrofit2.HttpException) {
            val raw = e.response()?.errorBody()?.string().orEmpty()
            val msg = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.get(1)
            error(msg ?: "Server error ${e.code()}")
        }
    }

    /** Quick top-up used by the practice screen (3 API calls). Returns questions added. */
    suspend fun refreshFromApi(subjectName: String): Int {
        val r = syncSubject(subjectName, 3)
        r.error?.let { error(it) }
        return r.added
    }

    /**
     * Downloads questions for one subject, using at most [maxCalls] API calls.
     * Every batch is saved immediately, so progress is kept even if it stops early.
     * Stops on its own after 3 calls in a row that bring no new questions (subject saturated).
     */
    suspend fun syncSubject(
        subjectName: String,
        maxCalls: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncResult = withContext(Dispatchers.IO) {
        val key = Subjects.keyByName[subjectName] ?: return@withContext SyncResult(0, 0, "Unknown subject")
        if (!AlocApi.hasToken) {
            return@withContext SyncResult(0, 0, "No ALOC token in this build. Add the ALOC_TOKEN secret and rebuild.")
        }
        val before = qDao.countFor(subjectName)
        var calls = 0
        var stale = 0
        var error: String? = null
        while (calls < maxCalls && stale < 3) {
            val beforeBatch = qDao.countFor(subjectName)
            try {
                fetchBatch(key, subjectName)
            } catch (e: Exception) {
                error = e.message ?: "Could not reach ALOC"
                break
            }
            calls++
            val gained = qDao.countFor(subjectName) - beforeBatch
            stale = if (gained == 0) stale + 1 else 0
            onProgress(calls, maxCalls)
            delay(300)
        }
        SyncResult(qDao.countFor(subjectName) - before, calls, error)
    }
}

data class SyncResult(val added: Int, val calls: Int, val error: String?)
