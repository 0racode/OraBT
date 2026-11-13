package com.pastq.cbt.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pastq.cbt.data.AnswerItem
import com.pastq.cbt.data.AppScope
import com.pastq.cbt.data.AuthManager
import com.pastq.cbt.data.ExamResultEntity
import com.pastq.cbt.data.ScoreStatus
import com.pastq.cbt.data.SubmitRequest
import com.pastq.cbt.data.QuestionEntity
import com.pastq.cbt.data.Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PracticeViewModel(private val repo: Repository) : ViewModel() {
    var questions by mutableStateOf<List<QuestionEntity>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var index by mutableIntStateOf(0)
    val picked: SnapshotStateMap<Int, Char> = mutableStateMapOf()

    init {
        viewModelScope.launch {
            questions = repo.practice(Session.practiceSubject, Session.practiceType, Session.practiceYear)
            loading = false
        }
    }

    fun pick(letter: Char) {
        if (picked[index] == null) picked[index] = letter
    }
}

class ExamViewModel(private val repo: Repository) : ViewModel() {
    var questions by mutableStateOf<List<QuestionEntity>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var index by mutableIntStateOf(0)
    var remaining by mutableIntStateOf(0)
        private set
    var finished by mutableStateOf(false)
        private set
    val answers: SnapshotStateMap<Int, Char> = mutableStateMapOf()

    private var totalSeconds = 0
    private var timer: Job? = null
    private val config = Session.examConfig

    init {
        viewModelScope.launch {
            val cfg = config
            if (cfg == null) {
                loading = false
                return@launch
            }
            questions = cfg.subjects.flatMap { subject ->
                val want = cfg.quotas[subject] ?: 20
                val first = repo.random(subject, cfg.type, want)
                if (first.size >= want || cfg.type.isEmpty()) {
                    first
                } else {
                    // Not enough of this exam type: top up with other types so the quota is met.
                    val have = first.map { it.id }.toSet()
                    first + repo.random(subject, "", want * 2).filter { it.id !in have }.take(want - first.size)
                }
            }
            totalSeconds = cfg.minutes * 60
            remaining = totalSeconds
            loading = false
            timer = launch {
                while (remaining > 0 && !finished) {
                    delay(1000)
                    remaining -= 1
                }
                if (!finished) submit()
            }
        }
    }

    fun select(letter: Char) {
        if (!finished) answers[index] = letter
    }

    fun submit() {
        if (finished) return
        finished = true
        timer?.cancel()
        val review = questions.mapIndexed { i, q -> ReviewItem(q, answers[i]) }
        val correct = review.count { it.picked == it.q.answer.firstOrNull() }
        val used = totalSeconds - remaining
        val title = config?.title ?: "Exam"
        Session.review = review
        val perSubject = review.groupBy { it.q.subject }.map { (subject, items) ->
            "$subject ${items.count { it.picked == it.q.answer.firstOrNull() }}/${items.size}"
        }
        val mode = config?.mode ?: "Custom"
        Session.outcome = ExamOutcome(title, mode, questions.size, correct, used, perSubject)
        // AppScope (not viewModelScope) so saving and ranking finish even after this screen closes.
        val subjectsText = config?.subjects?.joinToString(", ") ?: ""
        AppScope.launch {
            repo.saveResult(
                ExamResultEntity(
                    title = title, total = questions.size, correct = correct,
                    seconds = used, timestamp = System.currentTimeMillis(),
                    mode = mode, subjects = subjectsText,
                    breakdown = perSubject.joinToString(" · "),
                )
            )
        }
        Session.scoreStatus.value = when {
            AuthManager.token == null -> ScoreStatus.SignedOut
            questions.size < 20 -> ScoreStatus.TooShort(questions.size)
            else -> ScoreStatus.Submitting
        }
        if (Session.scoreStatus.value is ScoreStatus.Submitting) {
            val request = SubmitRequest(
                mode = mode, subjects = subjectsText, seconds = used,
                answers = review.map { AnswerItem(it.q.id, it.picked?.toString()) },
            )
            AppScope.launch { Session.scoreStatus.value = repo.submitScore(request) }
        }
    }
}
