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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.AppScope
import com.pastq.cbt.data.AuthManager
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
    // Signed-in players are not asked for a name: their account name (or email) is used.
    val account by AuthManager.account.collectAsStateWithLifecycle()
    val canJoin = !busy && code.length >= 4 && (account != null || name.trim().length >= 2)

    Scaffold(
        topBar = { BackBar("Live test", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(
                    enabled = canJoin,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            LiveManager.join(code, account?.displayName ?: name, autoName = account != null).fold(
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
                if (account != null) "Enter the code your teacher gave you."
                else "Enter the code your teacher gave you, and a name for the scoreboard. Nobody else in the session can use the same name.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                code, { code = it.filter { ch -> ch.isLetterOrDigit() }.take(8).uppercase() },
                label = { Text("Session code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            if (account != null) {
                Text("You will join as ${account!!.displayName}.", style = MaterialTheme.typography.titleSmall)
                Text("This is the name on your account. Change it in your profile.", style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(
                    name, { name = it.take(40) }, label = { Text("Your name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "If the test has already started, your request goes to the admin and you can begin once they allow you in.",
                style = MaterialTheme.typography.bodySmall,
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
    // When a scheduled test is due to start, on this phone's clock (0 = no schedule). Counted from the server's "seconds to go".
    var startsAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var submitted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    // Set the moment the player leaves the app during the test; the test stays closed until the admin lets them back in.
    var leftApp by remember { mutableStateOf(false) }
    var lockSeen by remember { mutableStateOf(false) }
    var requestSent by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submitNow() {
        if (submitting || submitted) return
        scope.launch {
            submitting = true
            LiveManager.submit().fold(
                onSuccess = {
                    submitted = true
                    // When the admin allows it, the score shows straight away instead of waiting for the next refresh.
                    runCatching { LiveManager.results() }.getOrNull()?.let { if (it.ready) results = it }
                },
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
                startsAt = if (s.status == "waiting" && s.scheduled_start_at != null) System.currentTimeMillis() + s.starts_in_seconds * 1000L else 0L
                if (s.submitted) submitted = true
                if (s.locked) lockSeen = true
                if (leftApp && s.status == "running" && !s.submitted && s.join_status == "approved" && !s.locked) {
                    // Either the admin let this player back in, or the server never got the "left the app" message (send it again).
                    if (lockSeen) { leftApp = false; lockSeen = false; requestSent = false } else LiveManager.leave()
                }
                if (s.status == "running" && !s.submitted && !s.locked && s.join_status == "approved") {
                    if (s.questions.isNotEmpty()) {
                        questions = s.questions
                        LiveManager.restoreAnswers(s.saved_answers)
                    }
                    deadline = System.currentTimeMillis() + s.seconds_left * 1000L
                }
                // Results are asked for once the session ended, or right after this player submitted when the admin shows scores immediately.
                if (s.join_status == "approved" && (s.status == "ended" || (s.submitted && s.score_visibility == "immediate"))) {
                    val r = LiveManager.results()
                    results = r
                    if (r.ready && r.final) break
                }
            } catch (e: Exception) {
                error = e.friendlyMessage()
            }
            // Slower refresh while waiting for the session to end with the score already on screen.
            val waitingOnAdmin = state?.join_status == "pending" || state?.locked == true || leftApp
            // In the last seconds before a scheduled start, ask every second so the test opens right on time.
            val startingSoon = startsAt > 0L && startsAt - System.currentTimeMillis() < 8000
            delay(if (startingSoon) 1000 else if (waitingOnAdmin) 3000 else if (results?.ready == true) 8000 else if (state?.status == "running" && !submitted) 7000 else 2500)
        }
    }
    // Clock for the countdown.
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(500) }
    }

    val s = state
    val remaining = ((deadline - now) / 1000).toInt().coerceAtLeast(0)
    val testing = s?.status == "running" && !submitted && questions.isNotEmpty() && !leftApp && s.locked.not() && s.join_status == "approved"

    // Leaving the app (home button, switching apps, locking the phone) during the test closes it for this player.
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostActivity = ctx.findHostActivity()
    DisposableEffect(lifecycleOwner, testing) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && testing && hostActivity?.isChangingConfigurations != true) {
                leftApp = true
                lockSeen = false
                AppScope.launch { LiveManager.leave() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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
        s != null && s.join_status == "pending" -> Scaffold(topBar = { BackBar(s.title.ifBlank { "Live test" }, onBack) }) { pad ->
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("Waiting for the admin", style = MaterialTheme.typography.titleLarge)
                Text(
                    "The test has already started, so your request to join was sent to the admin. Keep this screen open. The test opens as soon as they allow you in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { LiveManager.forget(); onBack() }) { Text("Cancel my request") }
            }
        }
        s != null && s.join_status == "denied" -> Scaffold(topBar = { BackBar(s.title.ifBlank { "Live test" }, onBack) }) { pad ->
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Request declined", style = MaterialTheme.typography.titleLarge)
                Text("The admin did not let you into this test.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { LiveManager.forget(); onBack() }) { Text("Leave") }
            }
        }
        s != null && s.status == "running" && !submitted && (leftApp || s.locked) -> Scaffold(topBar = { BackBar(s.title.ifBlank { "Live test" }, onBack) }) { pad ->
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (s.strict_leave) "Your test has ended" else "You left the test", style = MaterialTheme.typography.titleLarge)
                if (s.strict_leave) Text(
                    "This test is strict: leaving the app closes it for you, and you cannot come back to it. Your answers so far are being graded.",
                    style = MaterialTheme.typography.bodyMedium,
                ) else Text(
                    "Leaving the app during a live test closes it for you. Your answers so far are saved. Ask the admin to let you back in; if they allow it you continue where you stopped, and the clock does not stop meanwhile.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (s.strict_leave) {
                    // No way back in a strict test.
                } else if (requestSent || s.rejoin_requested) {
                    CircularProgressIndicator()
                    Text("Request sent. Waiting for the admin...", style = MaterialTheme.typography.titleSmall)
                } else {
                    Button(onClick = {
                        scope.launch {
                            requestError = null
                            LiveManager.rejoinRequest().fold(
                                onSuccess = { requestSent = true },
                                onFailure = { requestError = it.friendlyMessage() },
                            )
                        }
                    }) { Text("Ask the admin to let me back in") }
                }
                requestError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
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
                    s.status == "waiting" && startsAt > 0L -> {
                        val untilStart = ((startsAt - now) / 1000).toInt().coerceAtLeast(0)
                        val startMs = parseIsoUtc(s.scheduled_start_at)
                        Text("Starts in", style = MaterialTheme.typography.titleMedium)
                        Text(countdownLabel(untilStart), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        if (startMs != null) Text("Scheduled for ${formatWhen(startMs)}", style = MaterialTheme.typography.bodyMedium)
                        Text("${s.players} joined · ${s.minutes} minutes · you are ${s.name}")
                        Text(
                            if (s.timer_mode == "personal") "You get the full ${s.minutes} minutes from the moment you begin."
                            else "The test opens by itself at the start time and the clock runs for everyone. Keep this screen open.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    s.status == "waiting" -> {
                        CircularProgressIndicator()
                        Text("Waiting for the admin to start", style = MaterialTheme.typography.titleLarge)
                        Text("${s.players} joined · ${s.minutes} minutes · you are ${s.name}")
                        Text(
                            if (s.timer_mode == "personal") "You get the full ${s.minutes} minutes from the moment you start, even if you join late."
                            else "The clock starts for everyone when the admin starts the test.",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                // While the test is still running, leaving must not forget the session or the corrections could never be shown.
                Button(onClick = { if (r.final) LiveManager.forget(); onBack() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (r.final) "Done" else "Close for now")
                }
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
                                if (r.rank != null) "Position ${r.rank} of ${r.participants} who finished" + (if (r.final) "" else " so far") else "Not ranked",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!r.final) {
                                Text(
                                    "Others are still taking the test, so your position can change. The corrections appear when the test ends; open this screen again then.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
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
            if (r.review.isNotEmpty()) {
                item { Text("Corrections", style = MaterialTheme.typography.titleMedium) }
                items(r.review) { item -> ReviewCard(item) }
            }
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
                    Text(row.name + (if (row.you) " (you)" else "") + (if (row.left) " (left app)" else ""), style = MaterialTheme.typography.bodyLarge)
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


/** The Activity behind a Compose context (the context can be wrapped several times). */
private fun android.content.Context.findHostActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
