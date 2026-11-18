@file:OptIn(ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.LiveBoardRow
import com.pastq.cbt.data.LiveItem
import com.pastq.cbt.data.LiveManager
import com.pastq.cbt.data.LiveResults
import com.pastq.cbt.data.LiveState
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveJoinScreen(onBack: () -> Unit, onJoined: () -> Unit, onAdmin: () -> Unit) {
    val current by LiveManager.joined.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val pending by LiveManager.pendingCode.collectAsStateWithLifecycle()
    LaunchedEffect(pending) { pending?.let { code = it.filter { ch -> ch.isLetterOrDigit() }.take(8).uppercase(); LiveManager.pendingCode.value = null } }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canJoin = !busy && code.length >= 4 && name.trim().length >= 2

    Scaffold(
        topBar = { BackBar("Live test", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(
                    enabled = canJoin,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            LiveManager.join(code, name).fold(
                                onSuccess = { onJoined() },
                                onFailure = { error = it.friendlyMessage() },
                            )
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Joining..." else "Join session") }
                TextButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) { Text("I'm the admin") }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            current?.let { c ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("You are in \"${c.title}\" as ${c.name}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onJoined) { Text("Continue") }
                            OutlinedButton(onClick = { LiveManager.forget() }) { Text("Leave") }
                        }
                    }
                }
            }
            Text(
                "Enter the code your teacher gave you, and a name for the scoreboard. Nobody else in the session can use the same name.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                code, { code = it.filter { ch -> ch.isLetterOrDigit() }.take(8).uppercase() },
                label = { Text("Session code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                name, { name = it.take(24) }, label = { Text("Your name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun LivePlayScreen(onBack: () -> Unit) {
    val joined by LiveManager.joined.collectAsStateWithLifecycle()
    val answers by LiveManager.answers.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf<LiveState?>(null) }
    var results by remember { mutableStateOf<LiveResults?>(null) }
    var questions by remember { mutableStateOf<List<LiveItem>>(emptyList()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(questions) { QuestionImages.prefetch(ctx, questions.map { it.image }) }
    var error by remember { mutableStateOf<String?>(null) }
    var deadline by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var submitted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submitNow() {
        if (submitting || submitted) return
        scope.launch {
            submitting = true
            LiveManager.submit().fold(
                onSuccess = { submitted = true },
                onFailure = {
                    val m = it.friendlyMessage()
                    // "already submitted" or "closed" both mean there is nothing more to send.
                    if (m.contains("already") || m.contains("closed")) submitted = true else error = m
                },
            )
            submitting = false
        }
    }

    // Keeps the screen in step with the server.
    LaunchedEffect(joined?.token) {
        if (joined == null) return@LaunchedEffect
        while (true) {
            try {
                val s = LiveManager.state()
                error = null
                state = s
                if (s.submitted) submitted = true
                if (s.status == "running" && !s.submitted) {
                    if (s.questions.isNotEmpty()) questions = s.questions
                    deadline = System.currentTimeMillis() + s.seconds_left * 1000L
                }
                if (s.status == "ended") {
                    val r = LiveManager.results()
                    results = r
                    if (r.ready) break
                }
            } catch (e: Exception) {
                error = e.friendlyMessage()
            }
            delay(if (state?.status == "running" && !submitted) 7000 else 2500)
        }
    }
    // Clock for the countdown.
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }

    val s = state
    val remaining = ((deadline - now) / 1000).toInt().coerceAtLeast(0)
    val testing = s?.status == "running" && !submitted && questions.isNotEmpty()
    LaunchedEffect(remaining, testing) {
        if (testing && remaining <= 0 && deadline > 0) submitNow()
    }
    // The admin ended the session while the student was still answering.
    LaunchedEffect(s?.status, testing) {
        if (s?.status == "ended" && !submitted && questions.isNotEmpty()) submitNow()
    }

    if (confirm) {
        val left = questions.count { answers[it.pos] == null }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Submit your answers?") },
            text = { Text(if (left > 0) "$left question(s) are not answered yet." else "You have answered every question.") },
            confirmButton = { TextButton(onClick = { confirm = false; submitNow() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep going") } },
        )
    }

    val r = results
    when {
        joined == null -> Scaffold(topBar = { BackBar("Live test", onBack) }) { pad ->
            Text("You are not in a live session.", Modifier.padding(pad).padding(16.dp))
        }
        r != null && r.ready -> LiveResultsContent(r, onBack)
        s == null -> Scaffold(topBar = { BackBar("Live test", onBack) }) { pad ->
            Column(Modifier.padding(pad).padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error == null) CircularProgressIndicator() else {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Text("Trying again...", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { LiveManager.forget(); onBack() }) { Text("Leave this session") }
                }
            }
        }
        testing -> LiveTest(
            title = s.title, questions = questions, answers = answers, remaining = remaining,
            submitting = submitting, error = error, onBack = onBack, onSubmit = { confirm = true },
        )
        else -> Scaffold(topBar = { BackBar(s.title.ifBlank { "Live test" }, onBack) }) { pad ->
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    s.status == "waiting" -> {
                        CircularProgressIndicator()
                        Text("Waiting for the admin to start", style = MaterialTheme.typography.titleLarge)
                        Text("${s.players} joined · ${s.minutes} minutes · you are ${s.name}")
                    }
                    s.status == "running" && submitted -> {
                        Text("Answers submitted", style = MaterialTheme.typography.titleLarge)
                        Text("Your corrections and position appear when the session ends (${formatTime(remaining)} left, or when the admin ends it).", style = MaterialTheme.typography.bodyMedium)
                    }
                    s.status == "ended" -> {
                        CircularProgressIndicator()
                        Text("Session ended. Getting your results...")
                    }
                    else -> CircularProgressIndicator()
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun LiveTest(
    title: String,
    questions: List<LiveItem>,
    answers: Map<Int, Char>,
    remaining: Int,
    submitting: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    val q = questions[index.coerceIn(0, questions.lastIndex)]
    Scaffold(
        topBar = {
            Column {
                BackBar(title.ifBlank { "Live test" }, onBack)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${answers.size}/${questions.size} answered", style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatTime(remaining), fontWeight = FontWeight.Bold,
                        color = if (remaining < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(questions) { i, item ->
                        FilterChip(
                            selected = i == index,
                            onClick = { index = i },
                            label = { Text("${i + 1}", color = if (answers[item.pos] != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        )
                    }
                }
            }
        },
        bottomBar = {
            BottomActionBar {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                    if (index < questions.lastIndex) {
                        Button(onClick = { index++ }, modifier = Modifier.weight(1f)) { Text("Next") }
                    } else {
                        Button(onClick = onSubmit, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(if (submitting) "Sending..." else "Submit") }
                    }
                }
                if (index < questions.lastIndex) {
                    TextButton(onClick = onSubmit, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text("Submit now") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            QuestionCard(
                q = q.asQuestion(),
                picked = answers[q.pos],
                showAnswer = false,
                onPick = { LiveManager.pick(q.pos, it) },
            )
        }
    }
}

@Composable
fun LiveResultsContent(r: LiveResults, onBack: () -> Unit) {
    val pct = if (r.total > 0) r.correct * 100.0 / r.total else 0.0
    Scaffold(
        topBar = { BackBar(r.title.ifBlank { "Live test results" }, onBack) },
        bottomBar = {
            BottomActionBar {
                Button(onClick = { LiveManager.forget(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        if (r.submitted) {
                            Text("${r.correct} / ${r.total}  (${"%.1f".format(pct)}%)", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                if (r.rank != null) "Position ${r.rank} of ${r.participants} who finished" else "Not ranked",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Time used: ${formatTime(r.seconds)}", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("You did not submit answers before the session ended.", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (r.board.isNotEmpty()) {
                item { Text("Scoreboard", style = MaterialTheme.typography.titleMedium) }
                items(r.board) { row -> BoardRow(row) }
            }
            item { Text("Corrections", style = MaterialTheme.typography.titleMedium) }
            items(r.review) { item -> ReviewCard(item) }
            item { Text("", Modifier.padding(8.dp)) }
        }
    }
}

@Composable
fun BoardRow(row: LiveBoardRow) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (row.you) MaterialTheme.colorScheme.tertiaryContainer
            else if (row.rank <= 3) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${row.rank}", fontWeight = FontWeight.Bold)
                Column {
                    Text(row.name + if (row.you) " (you)" else "", style = MaterialTheme.typography.bodyLarge)
                    Text(formatTime(row.seconds), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("${row.correct}/${row.total}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One question after the test: your pick, the correct option highlighted, and the explanation. */
@Composable
fun ReviewCard(item: LiveItem) {
    val correct = item.answer.firstOrNull()
    val picked = item.picked?.firstOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Q${item.pos}" + if (item.subject.isNotBlank()) " · ${item.subject}" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            if (item.section.isNotBlank()) {
                HtmlText(item.section, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HtmlText(item.question, style = MaterialTheme.typography.titleSmall)
            QuestionImage(item.image)
            listOf('a', 'b', 'c', 'd', 'e').forEach { letter ->
                val text = item.optionText(letter)
                if (text.isBlank()) return@forEach
                val container = when {
                    letter == correct -> MaterialTheme.colorScheme.tertiaryContainer
                    letter == picked -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val tag = when {
                    letter == correct && letter == picked -> "  ✓ your answer, correct"
                    letter == correct -> "  ✓ correct answer"
                    letter == picked -> "  ✗ your answer"
                    else -> ""
                }
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = container),
                    border = if (letter == correct) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
                ) {
                    HtmlText(text, Modifier.padding(12.dp), prefix = "${letter.uppercaseChar()}.  ", suffix = tag, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (picked == null) Text("You did not answer this question.", style = MaterialTheme.typography.bodySmall)
            when {
                item.explanation.isNotBlank() -> HtmlText(item.explanation, style = MaterialTheme.typography.bodyMedium)
                item.qid != null -> ExplanationBox(item.qid)
            }
            if (item.qid != null) ReportButton(item.qid)
        }
    }
}
