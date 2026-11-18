@file:OptIn(ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.pastq.cbt.data.ReportInfo
import com.pastq.cbt.data.Reporter
import com.pastq.cbt.data.Repository
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var announce by remember { mutableStateOf(true) }
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
                                    announce = announce,
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notify everyone", style = MaterialTheme.typography.titleMedium)
                    Text("Sends a phone notice with the code when you create the test and when you start it. Turn off for a private test.", style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.material3.Switch(checked = announce, onCheckedChange = { announce = it })
            }

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

/** Escapes one CSV cell per RFC 4180 (quotes the value if it holds a comma, quote or newline). */
private fun csvCell(s: String): String {
    val escaped = s.replace("\"", "\"\"")
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

// Last column, "action", is left blank on export; fill it in and re-import the same file to apply
// corrections. Accepted values: approve, remove, or a/b/c/d/e to set that as the correct option.
private val DISPUTED_CSV_HEADER = listOf(
    "id", "status", "subject", "question", "section", "image",
    "a", "b", "c", "d", "e", "stored_answer", "ai_answer", "ai_explanation", "action",
)

private fun DisputedItem.toCsvRow(): String = listOf(
    id, review_status, subject, question, section, image,
    a, b, c, d, e, answer, ai_answer ?: "", ai_explanation ?: "", "",
).joinToString(",") { csvCell(it) }

private fun buildDisputedCsv(items: List<DisputedItem>): String = buildString {
    append(DISPUTED_CSV_HEADER.joinToString(",") { csvCell(it) }).append("\r\n")
    for (it in items) append(it.toCsvRow()).append("\r\n")
}

private val IMPORT_ACTIONS = setOf("approve", "remove", "a", "b", "c", "d", "e")

/** One correction read from an uploaded file: do this to the question with this id. */
private data class ImportRow(val id: String, val action: String)

private data class ImportParseResult(val rows: List<ImportRow>, val skippedNoId: Int, val skippedNoAction: Int, val skippedBadAction: Int)

/** Minimal RFC 4180 reader: handles quoted fields, escaped quotes, and commas/newlines inside quotes. */
private fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var field = StringBuilder()
    var row = mutableListOf<String>()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = false
                else -> field.append(c)
            }
        } else {
            when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field = StringBuilder() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field = StringBuilder(); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
    return rows
}

@Serializable
private data class ImportRowJson(val id: String = "", val action: String = "")

@Serializable
private data class ImportWrapperJson(val items: List<ImportRowJson> = emptyList())

private fun parseImportRows(text: String): ImportParseResult {
    var noId = 0; var noAction = 0; var badAction = 0
    val out = mutableListOf<ImportRow>()
    fun consider(id: String, action: String) {
        val cid = id.trim()
        val act = action.trim().lowercase()
        when {
            cid.isEmpty() -> noId++
            act.isEmpty() -> noAction++
            act !in IMPORT_ACTIONS -> badAction++
            else -> out.add(ImportRow(cid, act))
        }
    }
    val trimmed = text.trimStart()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        val list = runCatching { LiveManager.json.decodeFromString<List<ImportRowJson>>(text) }
            .getOrElse { LiveManager.json.decodeFromString<ImportWrapperJson>(text).items }
        for (r in list) consider(r.id, r.action)
    } else {
        val table = parseCsv(text)
        if (table.isEmpty()) return ImportParseResult(emptyList(), 0, 0, 0)
        val header = table[0].map { it.trim().lowercase() }
        val idIdx = header.indexOf("id")
        val actionIdx = header.indexOf("action")
        if (idIdx < 0 || actionIdx < 0) {
            throw IllegalArgumentException("The file needs \"id\" and \"action\" columns.")
        }
        for (r in table.drop(1)) {
            if (r.size <= 1 && r.getOrNull(0).isNullOrBlank()) continue
            consider(r.getOrNull(idIdx) ?: "", r.getOrNull(actionIdx) ?: "")
        }
    }
    return ImportParseResult(out, noId, noAction, badAction)
}

@Composable
fun AdminDisputedScreen(onBack: () -> Unit) {
    val rows = remember { mutableStateListOf<DisputedItem>() }
    var offset by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var removing by remember { mutableStateOf<DisputedItem?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var exportMenu by remember { mutableStateOf(false) }
    var exportingAll by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ImportParseResult?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0 to 0) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reports = remember { mutableStateMapOf<String, List<ReportInfo>>() }

    fun refreshList() {
        rows.clear(); reports.clear(); offset = 0; more = true; selected.clear(); reload++
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { it.write(csv) }
                }
                error = null
            } catch (e: Exception) {
                error = "Couldn't save the file: ${e.message}"
            }
        }
    }

    fun exportCsv(items: List<DisputedItem>) {
        if (items.isEmpty()) { error = "Nothing to export."; return }
        pendingCsv = buildDisputedCsv(items)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        saveLauncher.launch("pastq_review_$stamp.csv")
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } ?: throw IllegalArgumentException("Couldn't read that file.")
                importPreview = withContext(Dispatchers.IO) { parseImportRows(text) }
            } catch (e: Exception) {
                error = "Couldn't read the file: ${e.message ?: "unknown error"}"
            }
        }
    }

    LaunchedEffect(reload) {
        loading = true
        try {
            val r = LiveManager.adminDisputed(offset)
            rows.addAll(r.items)
            reports.putAll(Reporter.adminReports(r.items.map { it.id }))
            offset += r.items.size
            more = r.items.size >= 20
            error = null
        } catch (e: Exception) {
            error = e.friendlyMessage()
        }
        loading = false
    }

    removing?.let { q ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this question?") },
            text = { Text("It is deleted for everyone and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = q; removing = null
                    scope.launch {
                        try { LiveManager.adminRemove(target.id); rows.remove(target); selected.remove(target.id); error = null }
                        catch (e: Exception) { error = e.friendlyMessage() }
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    importPreview?.let { p ->
        val approve = p.rows.count { it.action == "approve" }
        val remove = p.rows.count { it.action == "remove" }
        val rekey = p.rows.size - approve - remove
        val skipped = p.skippedNoId + p.skippedNoAction + p.skippedBadAction
        AlertDialog(
            onDismissRequest = { if (!importBusy) importPreview = null },
            title = { Text("Apply corrections?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (importBusy) {
                        Text("Applying ${importProgress.first} of ${importProgress.second}…")
                    } else {
                        if (p.rows.isEmpty()) {
                            Text("No usable rows found. Each row needs an id and an action (approve, remove, or a/b/c/d/e).")
                        } else {
                            Text("$approve to approve as-is, $rekey to re-key, $remove to remove. ${p.rows.size} total.")
                        }
                        if (skipped > 0) {
                            Text(
                                "$skipped row(s) skipped (${p.skippedNoId} no id, ${p.skippedNoAction} no action, ${p.skippedBadAction} unrecognized action).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !importBusy && p.rows.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            importBusy = true
                            var ok = 0
                            val failures = mutableListOf<String>()
                            p.rows.forEachIndexed { i, row ->
                                importProgress = (i + 1) to p.rows.size
                                try {
                                    when (row.action) {
                                        "approve" -> LiveManager.adminApprove(row.id)
                                        "remove" -> LiveManager.adminRemove(row.id)
                                        else -> LiveManager.adminSetKey(row.id, row.action[0])
                                    }
                                    ok++
                                } catch (e: Exception) {
                                    failures.add("${row.id}: ${e.friendlyMessage()}")
                                }
                            }
                            importBusy = false
                            importPreview = null
                            importResult = buildString {
                                append("$ok of ${p.rows.size} applied.")
                                if (failures.isNotEmpty()) {
                                    append("\n\nFailed:\n")
                                    append(failures.take(10).joinToString("\n"))
                                    if (failures.size > 10) append("\n…and ${failures.size - 10} more.")
                                }
                            }
                            refreshList()
                        }
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(enabled = !importBusy, onClick = { importPreview = null }) { Text("Cancel") } },
        )
    }

    importResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { importResult = null },
            title = { Text("Import finished") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { importResult = null }) { Text("OK") } },
        )
    }

    Scaffold(
        topBar = {
            BackBar("Review answer keys", onBack) {
                Box {
                    TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = !exportingAll) { Text("Import") }
                }
                Box {
                    TextButton(onClick = { exportMenu = true }, enabled = !exportingAll) {
                        if (exportingAll) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Export")
                    }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export selected (${selected.size})") },
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                exportMenu = false
                                exportCsv(rows.filter { it.id in selected })
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export all disputed/faulty") },
                            onClick = {
                                exportMenu = false
                                scope.launch {
                                    exportingAll = true
                                    try {
                                        val all = mutableListOf<DisputedItem>()
                                        var off = 0
                                        while (true) {
                                            val r = LiveManager.adminDisputed(off)
                                            if (r.items.isEmpty()) break
                                            all.addAll(r.items)
                                            off += r.items.size
                                            if (r.items.size < 20) break
                                        }
                                        exportCsv(all)
                                        error = null
                                    } catch (e: Exception) {
                                        error = e.friendlyMessage()
                                    }
                                    exportingAll = false
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Questions the AI disputed or could not verify. Approve to keep the stored answer, pick the correct option to fix it, or remove the question. Approving or fixing counts it as confirmed. Tick questions to export a bulk selection, or use Export > Export all to save everything in one CSV file. Fill in the blank \"action\" column (approve, remove, or a/b/c/d/e) and use Import to apply your corrections in one go.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (rows.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = {
                            if (selected.size == rows.size) selected.clear() else { selected.clear(); selected.addAll(rows.map { it.id }) }
                        }) { Text(if (selected.size == rows.size) "Clear selection" else "Select all loaded (${rows.size})") }
                        if (selected.isNotEmpty()) Text("${selected.size} selected", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(rows, key = { it.id }) { q ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = q.id in selected,
                                onCheckedChange = { checked -> if (checked) selected.add(q.id) else selected.remove(q.id) },
                            )
                            Text("${q.subject} · ${q.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (q.review_status == "unverifiable") "Unverifiable" else "Disputed",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (q.section.isNotBlank()) HtmlText(q.section, style = MaterialTheme.typography.bodySmall)
                        HtmlText(q.question, style = MaterialTheme.typography.titleSmall)
                        QuestionImage(q.image)
                        listOf('a' to q.a, 'b' to q.b, 'c' to q.c, 'd' to q.d, 'e' to q.e).forEach { (l, text) ->
                            if (text.isBlank()) return@forEach
                            val tags = listOfNotNull(
                                if (q.answer == l.toString()) "stored key" else null,
                                if (q.ai_answer == l.toString()) "AI says" else null,
                            ).joinToString(", ")
                            HtmlText(text, prefix = "${l.uppercaseChar()}. ", suffix = if (tags.isNotEmpty()) "   [$tags]" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                        q.ai_explanation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        reports[q.id]?.takeIf { it.isNotEmpty() }?.let { list ->
                            Text("Reported by players (${list.size}):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                            list.forEach { rep ->
                                Text(
                                    "• ${Reporter.label(rep.reason)}" + if (rep.note.isNotBlank()) ": ${rep.note}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Text("Correct option (or approve / remove):", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf('a' to q.a, 'b' to q.b, 'c' to q.c, 'd' to q.d, 'e' to q.e)
                                .filter { it.second.isNotBlank() }
                                .forEach { (l, _) ->
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            try { LiveManager.adminSetKey(q.id, l); rows.remove(q); selected.remove(q.id); error = null }
                                            catch (e: Exception) { error = e.friendlyMessage() }
                                        }
                                    }) { Text(l.uppercase()) }
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = {
                                scope.launch {
                                    try { LiveManager.adminApprove(q.id); rows.remove(q); selected.remove(q.id); error = null }
                                    catch (e: Exception) { error = e.friendlyMessage() }
                                }
                            }, modifier = Modifier.weight(1f)) { Text("Approve as-is") }
                            OutlinedButton(
                                onClick = { removing = q },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) { Text("Remove") }
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
