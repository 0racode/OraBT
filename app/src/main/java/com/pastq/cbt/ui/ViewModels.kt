package com.pastq.cbt.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pastq.cbt.data.ExamResultEntity
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
            questions = cfg.subjects.flatMap { repo.random(it, cfg.type, cfg.perSubject) }
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
        Session.outcome = ExamOutcome(title, questions.size, correct, used)
        viewModelScope.launch {
            repo.saveResult(
                ExamResultEntity(
                    title = title, total = questions.size, correct = correct,
                    seconds = used, timestamp = System.currentTimeMillis(),
                )
            )
        }
    }
}
