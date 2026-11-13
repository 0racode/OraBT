package com.pastq.cbt.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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

    /**
     * Tops up the local database from the live ALOC API. Returns the number of
     * questions fetched, or throws if the request fails (no token, offline, ...).
     */
    suspend fun refreshFromApi(subjectName: String, batches: Int = 3): Int = withContext(Dispatchers.IO) {
        val key = Subjects.keyByName[subjectName] ?: error("Unknown subject")
        check(AlocApi.hasToken) { "No ALOC token set. Add ALOC_TOKEN to local.properties." }
        var added = 0
        repeat(batches) {
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
            added += items.size
        }
        added
    }
}
