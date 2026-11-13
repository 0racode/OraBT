package com.pastq.cbt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One past question. [id] is "<subjectKey>-<number>", e.g. "chemistry-12". */
@Serializable
@Entity(tableName = "questions", indices = [Index("subject"), Index("type"), Index("year")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val question: String,
    val a: String,
    val b: String,
    val c: String,
    val d: String,
    val answer: String,
    val section: String = "",
    val solution: String = "",
    val type: String = "",
    val year: String = "",
) {
    fun optionText(letter: Char): String = when (letter) {
        'a' -> a
        'b' -> b
        'c' -> c
        else -> d
    }
}

@Entity(tableName = "results")
data class ExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val total: Int,
    val correct: Int,
    val seconds: Int,
    val timestamp: Long,
)

data class SubjectCount(val subject: String, val count: Int)
