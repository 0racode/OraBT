@file:OptIn(ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.AdminResults
import com.pastq.cbt.data.AdminSession
import com.pastq.cbt.data.DisputedItem
import com.pastq.cbt.data.DraftItem
import com.pastq.cbt.data.LiveManager
import com.pastq.cbt.data.Repository
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AdminLoginScreen(onBack: () -> Unit, onLoggedIn: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val admin by LiveManager.admin.collectAsStateWithLifecycle()
    LaunchedEffect(admin) { if (admin != null) onLoggedIn() }

    Scaffold(
        topBar = { BackBar("Admin sign in", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(
                    enabled = !busy && login.isNotBlank() && password.length >= 4,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            LiveManager.adminLogin(login, password).onFailure { error = it.friendlyMessage() }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Please wait..." else "Sign in") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sign in with the admin email or name.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(login, { login = it }, label = { Text("Email or name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
fun AdminHomeScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onResults: (String) -> Unit,
    onDisputed: () -> Unit,
) {
    val admin by LiveManager.admin.collectAsStateWithLifecycle()
    var sessions by remember { mutableStateOf<List<AdminSession>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf<AdminSession?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(admin) { if (admin == null) onBack() }
    LaunchedEffect(reload) {
        while (true) {
            try {
                sessions = LiveManager.adminList().sessions
                error = null
            } catch (e: Exception) {
                error = e.friendlyMessage()
            }
            delay(5000)
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try { block(); error = null } catch (e: Exception) { error = e.friendlyMessage() }
            reload++
        }
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${s.title}\"?") },
            text = { Text("Its questions, players and results are removed.") },
            confirmButton = { TextButton(onClick = { deleting = null; act { LiveManager.adminDelete(s.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = { BackBar("Live tests (admin)", onBack) },
        bottomBar = {
            BottomActionBar {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create a live test") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDisputed, modifier = Modifier.weight(1f)) { Text("Review answer keys") }
                    OutlinedButton(onClick = { LiveManager.signOutAdmin() }, modifier = Modifier.weight(1f)) { Text("Sign out") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val list = sessions
            when {
                list == null && error == null -> CircularProgressIndicator()
                list != null && list.isEmpty() -> Text("No live tests yet. Create one below.")
                list != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list) { s ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.title, style = MaterialTheme.typography.titleMedium)
                                Text("Code: ${s.code}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                val state = when (s.status) {
                                    "waiting" -> "Waiting to start"
                                    "running" -> "Running · ${formatTime(s.seconds_left)} left"
                                    else -> "Ended"
                                }
                                Text("$state · ${s.questions} questions · ${s.minutes} min", style = MaterialTheme.typography.bodyMedium)
                                Text("${s.players} joined · ${s.submitted} submitted", style = MaterialTheme.typography.bodySmall)
                                if (s.subjects.isNotBlank()) Text(s.subjects, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (s.status == "waiting") Button(onClick = { act { LiveManager.adminStart(s.id) } }) { Text("Start") }
                                    if (s.status == "running") Button(onClick = { act { LiveManager.adminEnd(s.id) } }) { Text("End now") }
                                    OutlinedButton(onClick = { onResults(s.id) }) { Text("Results") }
                                    TextButton(onClick = { deleting = s }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ManualDraft(
    val question: String = "",
    val section: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val explanation: String = "",
    val answer: Char? = null,
    val note: String? = null,
    val subject: String = "",
) {
    val valid get() = question.isNotBlank() && a.isNotBlank() && b.isNotBlank() && c.isNotBlank() && d.isNotBlank() && answer != null
}

@Composable
fun AdminCreateScreen(repo: Repository, onBack: () -> Unit, onCreated: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var title by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("0") }
    var mins by remember { mutableStateOf("40") }
    val picks = remember { mutableStateMapOf<String, String>() }
    val manual = remember { mutableStateListOf<ManualDraft>() }
    var paste by remember { mutableStateOf("") }
    var pasteSubject by remember { mutableStateOf("") }
    var drafting by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val minutes = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val timeOk = minutes in 1..120
    val pickTotal = picks.values.sumOf { it.toIntOrNull() ?: 0 }
    val total = pickTotal + manual.size
    val canCreate = !creating && timeOk && total in 1..200 && manual.all { it.valid }

    Scaffold(
        topBar = { BackBar("New live test", onBack) },
        bottomBar = {
            BottomActionBar {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("$total question(s) · ${if (timeOk) "$minutes minutes" else "set a time"}", style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = canCreate,
                    onClick = {
                        scope.launch {
                            creating = true; error = null
                            try {
                                LiveManager.adminCreate(
                                    title = title.ifBlank { "Live test" },
                                    minutes = minutes,
                                    picks = picks.mapValues { it.value.toIntOrNull() ?: 0 },
                                    manual = manual.map { DraftItem(it.question, it.section, it.a, it.b, it.c, it.d, "", it.explanation, false, true, it.subject) },
                                    answers = manual.map { it.answer ?: 'a' },
                                )
                                onCreated()
                            } catch (e: Exception) {
                                error = e.friendlyMessage()
                            }
                            creating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (creating) "Creating..." else "Create session") }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DurationInput(hours, mins, { hours = it }, { mins = it }, timeOk)

            Text("Questions from the bank", style = MaterialTheme.typography.titleMedium)
            Text("Type how many to draw at random from each subject. Only answer-checked questions are used.", style = MaterialTheme.typography.bodySmall)
            counts.forEach { sc ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${sc.subject} (${sc.count})", Modifier.weight(1f))
                    OutlinedTextField(
                        picks[sc.subject] ?: "", { picks[sc.subject] = it.filter(Char::isDigit).take(3) },
                        label = { Text("How many") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f),
                    )
                }
            }

            Text("Your own questions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Paste questions, with or without options. The AI tidies them and, when a question has no options, writes four. It never adds questions you did not paste. You confirm every correct answer.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(pasteSubject, { pasteSubject = it.take(60) }, label = { Text("Subject (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(paste, { paste = it.take(12000) }, label = { Text("Paste questions here") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !drafting && paste.trim().length >= 5,
                    onClick = {
                        scope.launch {
                            drafting = true; error = null
                            try {
                                val r = LiveManager.adminDraft(paste, pasteSubject)
                                if (r.items.isEmpty()) error = "The AI found no questions in that text."
                                r.items.forEach { it ->
                                    manual.add(
                                        ManualDraft(
                                            it.question, it.section, it.a, it.b, it.c, it.d, it.explanation,
                                            it.suggested_answer.firstOrNull()?.takeIf { ch -> ch in "abcd" },
                                            note = listOfNotNull(
                                                if (!it.had_options) "The AI wrote the options. Check them." else null,
                                                if (!it.confident) "The AI is not sure of the answer. Check it." else null,
                                            ).joinToString(" ").ifBlank { "AI suggests the answer shown. Check it." },
                                            subject = it.subject,
                                        )
                                    )
                                }
                                if (r.items.isNotEmpty()) paste = ""
                            } catch (e: Exception) {
                                error = e.friendlyMessage()
                            }
                            drafting = false
                        }
                    },
                ) { Text(if (drafting) "Tidying..." else "Tidy with AI") }
                OutlinedButton(onClick = { manual.add(ManualDraft(subject = pasteSubject)) }) { Text("Add blank question") }
            }

            manual.forEachIndexed { i, m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Question ${i + 1}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { manual.removeAt(i) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                        m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                        OutlinedTextField(m.question, { manual[i] = m.copy(question = it) }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.a, { manual[i] = m.copy(a = it) }, label = { Text("A") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.b, { manual[i] = m.copy(b = it) }, label = { Text("B") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.c, { manual[i] = m.copy(c = it) }, label = { Text("C") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(m.d, { manual[i] = m.copy(d = it) }, label = { Text("D") }, modifier = Modifier.fillMaxWidth())
                        Text("Correct answer", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf('a', 'b', 'c', 'd').forEach { l ->
                                FilterChip(selected = m.answer == l, onClick = { manual[i] = m.copy(answer = l) }, label = { Text(l.uppercase()) })
                            }
                        }
                        OutlinedTextField(m.explanation, { manual[i] = m.copy(explanation = it) }, label = { Text("Explanation (shown after the test)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun AdminResultsScreen(id: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<AdminResults?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        while (true) {
            try { data = LiveManager.adminResults(id); error = null } catch (e: Exception) { error = e.friendlyMessage() }
            delay(4000)
        }
    }
    Scaffold(topBar = { BackBar(data?.title?.ifBlank { null } ?: "Results", onBack) }) { pad ->
        val d = data
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (d == null) {
                item { CircularProgressIndicator() }
            } else {
                item {
                    Text("Code ${d.code} · " + when (d.status) {
                        "waiting" -> "waiting to start"
                        "running" -> "running, ${formatTime(d.seconds_left)} left"
                        else -> "ended"
                    }, style = MaterialTheme.typography.titleMedium)
                    Text("${d.joined.size} joined: ${d.joined.joinToString(", ").ifBlank { "nobody yet" }}", style = MaterialTheme.typography.bodySmall)
                }
                item { Text("Scoreboard (${d.ranked.size} submitted)", style = MaterialTheme.typography.titleMedium) }
                items(d.ranked) { BoardRow(it) }
            }
        }
    }
}

@Composable
fun AdminDisputedScreen(onBack: () -> Unit) {
    val rows = remember { mutableStateListOf<DisputedItem>() }
    var offset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        try {
            val r = LiveManager.adminDisputed(offset)
            rows.addAll(r.items)
            offset += r.items.size
            more = r.items.size >= 20
            error = null
        } catch (e: Exception) {
            error = e.friendlyMessage()
        }
        loading = false
    }

    Scaffold(topBar = { BackBar("Review answer keys", onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "The AI disagreed with the stored answer on these questions. Pick the correct option to fix it for everyone. Questions you set here count as confirmed.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(rows, key = { it.id }) { q ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${q.subject} · ${q.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (q.section.isNotBlank()) Text(q.section, style = MaterialTheme.typography.bodySmall)
                        Text(q.question, style = MaterialTheme.typography.titleSmall)
                        listOf('a' to q.a, 'b' to q.b, 'c' to q.c, 'd' to q.d).forEach { (l, text) ->
                            val tags = listOfNotNull(
                                if (q.answer == l.toString()) "stored key" else null,
                                if (q.ai_answer == l.toString()) "AI says" else null,
                            ).joinToString(", ")
                            Text("${l.uppercaseChar()}. $text" + if (tags.isNotEmpty()) "   [$tags]" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                        q.ai_explanation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Set the correct answer:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf('a', 'b', 'c', 'd').forEach { l ->
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        try {
                                            LiveManager.adminSetKey(q.id, l)
                                            rows.remove(q)
                                            error = null
                                        } catch (e: Exception) {
                                            error = e.friendlyMessage()
                                        }
                                    }
                                }) { Text(l.uppercase()) }
                            }
                        }
                    }
                }
            }
            item {
                when {
                    loading -> CircularProgressIndicator()
                    more -> OutlinedButton(onClick = { reload++ }) { Text("Load more") }
                    rows.isEmpty() -> Text("Nothing to review.")
                }
            }
        }
    }
}
