@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pastq.cbt.data.Repository
import java.text.DateFormat
import java.util.Date

@Composable
fun PracticeScreen(repo: Repository, onBack: () -> Unit) {
    val vm: PracticeViewModel = viewModel(factory = viewModelFactory { initializer { PracticeViewModel(repo) } })
    Scaffold(topBar = { BackBar(Session.practiceSubject, onBack) }) { pad ->
        when {
            vm.loading -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            vm.questions.isEmpty() -> Column(Modifier.padding(pad).padding(24.dp)) {
                Text("No questions match that filter. Go back and choose a different type or year.")
            }
            else -> Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text("Question ${vm.index + 1} of ${vm.questions.size}", style = MaterialTheme.typography.labelLarge)
                    QuestionCard(
                        q = vm.questions[vm.index],
                        picked = vm.picked[vm.index],
                        showAnswer = true,
                        onPick = vm::pick,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { vm.index -= 1 }, enabled = vm.index > 0, modifier = Modifier.weight(1f),
                    ) { Text("Previous") }
                    Button(
                        onClick = { vm.index += 1 }, enabled = vm.index < vm.questions.lastIndex, modifier = Modifier.weight(1f),
                    ) { Text("Next") }
                }
            }
        }
    }
}

/** Light-weight numbered cell for the question jump grid (cheap enough for 180+ questions). */
@Composable
private fun NumberCell(n: Int, current: Boolean, answered: Boolean, onClick: () -> Unit) {
    val bg = when {
        current -> MaterialTheme.colorScheme.primary
        answered -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("$n", color = fg, style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun ExamScreen(repo: Repository, onFinished: () -> Unit) {
    val vm: ExamViewModel = viewModel(factory = viewModelFactory { initializer { ExamViewModel(repo) } })
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(vm.finished) { if (vm.finished) onFinished() }

    if (confirm) {
        val unanswered = vm.questions.size - vm.answers.size
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Submit exam?") },
            text = { Text(if (unanswered > 0) "You have $unanswered unanswered question(s)." else "You have answered every question.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.submit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep going") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        formatTime(vm.remaining),
                        color = if (vm.remaining < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = { TextButton(onClick = { confirm = true }) { Text("Submit") } },
            )
        },
    ) { pad ->
        when {
            vm.loading -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            vm.questions.isEmpty() -> Column(Modifier.padding(pad).padding(24.dp)) { Text("No questions could be loaded.") }
            else -> Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(
                        "Question ${vm.index + 1} of ${vm.questions.size} · answered ${vm.answers.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    QuestionCard(
                        q = vm.questions[vm.index],
                        picked = vm.answers[vm.index],
                        showAnswer = false,
                        onPick = vm::select,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text("Jump to", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                    // Grouped by subject so UTME's four subjects are easy to navigate.
                    val subjects = vm.questions.map { it.subject }.distinct()
                    subjects.forEach { subject ->
                        Text(subject, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            vm.questions.forEachIndexed { i, q ->
                                if (q.subject == subject) {
                                    NumberCell(i + 1, current = i == vm.index, answered = vm.answers[i] != null) { vm.index = i }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.index -= 1 }, enabled = vm.index > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Button(onClick = { vm.index += 1 }, enabled = vm.index < vm.questions.lastIndex, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Composable
fun ResultScreen(onHome: () -> Unit) {
    val outcome = Session.outcome
    val review = Session.review
    Scaffold(
        topBar = { TopAppBar(title = { Text("Result") }) },
        bottomBar = {
            androidx.compose.material3.Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Button(onClick = onHome, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Back to home") }
            }
        },
    ) { pad ->
        if (outcome == null) {
            Column(Modifier.padding(pad).padding(24.dp)) { Text("No result to show.") }
            return@Scaffold
        }
        val pct = if (outcome.total == 0) 0 else outcome.correct * 100 / outcome.total
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${outcome.correct} / ${outcome.total} correct · ${formatTime(outcome.seconds)}")
                    Text(outcome.mode + " exam", style = MaterialTheme.typography.labelLarge)
                    outcome.breakdown.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item { Text("Review", style = MaterialTheme.typography.titleMedium) }
            items(review) { item ->
                QuestionCard(q = item.q, picked = item.picked ?: item.q.answer.firstOrNull(), showAnswer = true, onPick = {})
                if (item.picked == null) Text("Not answered", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HistoryScreen(repo: Repository, onBack: () -> Unit) {
    val results by repo.results.collectAsStateWithLifecycle(initialValue = emptyList())
    Scaffold(topBar = { BackBar("My results", onBack) }) { pad ->
        if (results.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp)) { Text("No exams taken yet.") }
        } else {
            LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r ->
                    val pct = if (r.total == 0) 0 else r.correct * 100 / r.total
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "$pct%  (${r.correct}/${r.total})" + if (r.mode.isNotBlank()) "  ·  ${r.mode}" else "",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Older results only have a title; newer ones list the subjects and per-subject scores.
                            Text(r.subjects.ifBlank { r.title }, style = MaterialTheme.typography.bodyMedium)
                            if (r.breakdown.isNotBlank()) Text(r.breakdown, style = MaterialTheme.typography.bodySmall)
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(r.timestamp)) + " · " + formatTime(r.seconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
