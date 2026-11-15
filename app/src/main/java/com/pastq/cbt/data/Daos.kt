package com.pastq.cbt.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QuestionEntity>)

    @Query("UPDATE questions SET reviewStatus = 'ok' WHERE reviewStatus = 'disputed'")
    suspend fun clearDisputed()

    @Query("UPDATE questions SET reviewStatus = 'disputed' WHERE id IN (:ids)")
    suspend fun markDisputed(ids: List<String>)

    @Query("SELECT COUNT(*) FROM questions WHERE subject = :subject")
    suspend fun countFor(subject: String): Int

    @Query("SELECT subject, COUNT(*) AS count FROM questions GROUP BY subject ORDER BY subject")
    fun subjectCounts(): Flow<List<SubjectCount>>

    @Query("SELECT DISTINCT type FROM questions WHERE subject = :subject AND type != '' ORDER BY type")
    suspend fun types(subject: String): List<String>

    @Query("SELECT DISTINCT year FROM questions WHERE subject = :subject AND year != '' ORDER BY year DESC")
    suspend fun years(subject: String): List<String>

    /** Practice list: empty-string filter means "any". [types] is a comma-separated list such as "utme,wassce". */
    @Query(
        "SELECT * FROM questions WHERE subject = :subject " +
            "AND (:types = '' OR instr(',' || :types || ',', ',' || type || ',') > 0) " +
            "AND (:year = '' OR year = :year) ORDER BY RANDOM()"
    )
    suspend fun practice(subject: String, types: String, year: String): List<QuestionEntity>

    @Query(
        "SELECT * FROM questions WHERE subject = :subject " +
            "AND (:type = '' OR type = :type) ORDER BY RANDOM() LIMIT :limit"
    )
    suspend fun random(subject: String, type: String, limit: Int): List<QuestionEntity>
}

@Dao
interface ResultDao {
    @Insert
    suspend fun insert(result: ExamResultEntity)

    @Query("DELETE FROM results WHERE timestamp < :cutoff")
    suspend fun deleteBefore(cutoff: Long)

    @Query("SELECT * FROM results ORDER BY timestamp DESC")
    fun all(): Flow<List<ExamResultEntity>>
}

@Database(entities = [QuestionEntity::class, ExamResultEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questions(): QuestionDao
    abstract fun results(): ResultDao
}
