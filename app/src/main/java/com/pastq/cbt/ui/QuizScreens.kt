@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
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
                title = { Text(formatTime(vm.remaining), color = if (vm.remaining < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
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
                    Text("Question ${vm.index + 1} of ${vm.questions.size}", style = MaterialTheme.typography.labelLarge)
                    QuestionCard(
                        q = vm.questions[vm.index],
                        picked = vm.answers[vm.index],
                        showAnswer = false,
                        onPick = vm::select,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text("Jump to", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.questions.indices.forEach { i ->
                            FilterChip(
                                selected = i == vm.index,
                                onClick = { vm.index = i },
                                label = { Text("${i + 1}") },
                                leadingIcon = null,
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    containerColor = if (vm.answers[i] != null) MaterialTheme.colorScheme.tertiaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                ),
                            )
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
    Scaffold(topBar = { TopAppBar(title = { Text("Result") }) }) { pad ->
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
                    Text(outcome.title, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onHome, modifier = Modifier.padding(top = 12.dp)) { Text("Back to home") }
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
                    androidx.compose.material3.Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("$pct%  (${r.correct}/${r.total})", style = MaterialTheme.typography.titleMedium)
                            Text(r.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(r.timestamp)) + " · " + formatTime(r.seconds),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
