package com.pastq.cbt.ui

import com.pastq.cbt.data.QuestionEntity
import com.pastq.cbt.data.ScoreStatus
import kotlinx.coroutines.flow.MutableStateFlow

data class ExamConfig(
    val title: String,
    val mode: String,
    val subjects: List<String>,
    /** Number of questions to draw for each subject. */
    val quotas: Map<String, Int>,
    val minutes: Int,
    val type: String,
)

data class ReviewItem(val q: QuestionEntity, val picked: Char?)

data class ExamOutcome(
    val title: String,
    val mode: String,
    val total: Int,
    val correct: Int,
    val seconds: Int,
    val breakdown: List<String>,
)

/** Small in-memory hand-off between screens (config in, results out). */
object Session {
    var practiceSubject: String = ""
    var practiceType: String = ""
    var practiceYear: String = ""

    var examConfig: ExamConfig? = null
    var review: List<ReviewItem> = emptyList()
    var outcome: ExamOutcome? = null
    val scoreStatus = MutableStateFlow<ScoreStatus>(ScoreStatus.Idle)
}

fun formatTime(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
