@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.Repository
import com.pastq.cbt.update.UpdateBanner
import com.pastq.cbt.update.VersionFooter
import kotlinx.coroutines.launch

private const val ENGLISH = "English Language"

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
    )
}

/** Fixed bar pinned to the bottom of a screen, so the main action never needs scrolling to. */
@Composable
private fun BottomActionBar(content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun SelectableRow(label: String, trailing: String, selected: Boolean, onClick: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (selected) "✓  $label" else label)
            Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HomeScreen(
    repo: Repository,
    onPractice: () -> Unit,
    onExam: () -> Unit,
    onHistory: () -> Unit,
    onDownload: () -> Unit,
) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var seeding by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repo.ensureSeeded()
        seeding = false
    }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UpdateBanner()
            Text("PastQ CBT", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("UTME, WASSCE & Post-UTME past questions", style = MaterialTheme.typography.bodyLarge)
            if (seeding) {
                CircularProgressIndicator()
                Text("Setting up your questions (first launch only)...")
            } else {
                Text("${counts.sumOf { it.count }} questions in ${counts.size} subjects, available offline")
                Button(onClick = onPractice, modifier = Modifier.fillMaxWidth()) { Text("Practice by subject") }
                Button(onClick = onExam, modifier = Modifier.fillMaxWidth()) { Text("Take a timed CBT exam") }
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("My results") }
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Download more questions") }
            }
            VersionFooter()
        }
    }
}

@Composable
fun PracticeSetupScreen(repo: Repository, onStart: () -> Unit, onBack: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var subject by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var types by remember { mutableStateOf(emptyList<String>()) }
    var years by remember { mutableStateOf(emptyList<String>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var fetching by remember { mutableStateOf(false) }

    LaunchedEffect(subject) {
        type = ""; year = ""
        if (subject.isNotEmpty()) {
            types = repo.types(subject)
            years = repo.years(subject)
        }
    }

    Scaffold(
        topBar = { BackBar("Practice", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar {
                Text(
                    if (subject.isEmpty()) "Pick a subject above" else subject,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        enabled = subject.isNotEmpty() && !fetching,
                        onClick = {
                            scope.launch {
                                fetching = true
                                val msg = try {
                                    val n = repo.refreshFromApi(subject)
                                    types = repo.types(subject); years = repo.years(subject)
                                    "Fetched $n questions from ALOC"
                                } catch (e: Exception) {
                                    e.message ?: "Could not reach ALOC"
                                }
                                fetching = false
                                snackbar.showSnackbar(msg)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (fetching) "Fetching..." else "Get more online") }
                    Button(
                        enabled = subject.isNotEmpty(),
                        onClick = {
                            Session.practiceSubject = subject
                            Session.practiceType = type
                            Session.practiceYear = year
                            onStart()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Start practice") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subject.isNotEmpty()) {
                // Filters sit above the list so they are always in view once a subject is chosen.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Exam type", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "", onClick = { type = "" }, label = { Text("Any") })
                        types.forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.uppercase()) })
                        }
                    }
                    Text("Year", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = year == "", onClick = { year = "" }, label = { Text("Any") })
                        years.forEach { y ->
                            FilterChip(selected = year == y, onClick = { year = y }, label = { Text(y) })
                        }
                    }
                }
            }
            Text("Choose a subject", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(counts) { sc ->
                    SelectableRow(sc.subject, "${sc.count}", subject == sc.subject) { subject = sc.subject }
                }
            }
        }
    }
}

/** Hours + minutes typed by the user. Valid range is 1 minute to 2 hours. */
@Composable
private fun DurationInput(
    hours: String,
    mins: String,
    onHours: (String) -> Unit,
    onMins: (String) -> Unit,
    valid: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Time allowed (2 hours maximum)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hours,
                onValueChange = { onHours(it.filter(Char::isDigit).take(1)) },
                label = { Text("Hours") },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
            OutlinedTextField(
                value = mins,
                onValueChange = { onMins(it.filter(Char::isDigit).take(3)) },
                label = { Text("Minutes") },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
        }
        if (!valid) {
            Text("Enter a time between 1 minute and 2 hours.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ExamSetupScreen(repo: Repository, onStart: () -> Unit, onBack: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var utme by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var perSubject by remember { mutableIntStateOf(20) }
    var type by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("2") }
    var mins by remember { mutableStateOf("0") }

    fun switchMode(toUtme: Boolean) {
        utme = toUtme
        chosen = emptySet()
        hours = if (toUtme) "2" else "0"
        mins = if (toUtme) "0" else "40"
    }

    val minutes = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val timeOk = minutes in 1..120
    val subjectsOk = if (utme) chosen.size == 3 else chosen.isNotEmpty()
    val orderedSubjects = if (utme) listOf(ENGLISH) + chosen.toList() else chosen.toList()
    val quotas: Map<String, Int> =
        if (utme) mapOf(ENGLISH to 60) + chosen.associateWith { 40 } else chosen.associateWith { perSubject }
    val total = quotas.values.sum()
    val maxSubjects = if (utme) 3 else 4

    Scaffold(
        topBar = { BackBar("CBT exam", onBack) },
        bottomBar = {
            BottomActionBar {
                val summary = when {
                    !subjectsOk && utme -> "Pick ${3 - chosen.size} more subject(s) with English"
                    !subjectsOk -> "Pick at least one subject"
                    !timeOk -> "Time must be 1 minute to 2 hours"
                    else -> "${orderedSubjects.size} subjects · $total questions · ${formatTime(minutes * 60)}"
                }
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                Button(
                    enabled = subjectsOk && timeOk,
                    onClick = {
                        val mode = if (utme) "UTME" else "Custom"
                        Session.examConfig = ExamConfig(
                            title = "$mode: " + orderedSubjects.joinToString(", "),
                            mode = mode,
                            subjects = orderedSubjects,
                            quotas = quotas,
                            minutes = minutes,
                            type = if (utme) "utme" else type,
                        )
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start exam") }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exam mode", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = utme, onClick = { switchMode(true) }, label = { Text("UTME (4 subjects)") })
                        FilterChip(selected = !utme, onClick = { switchMode(false) }, label = { Text("Custom") })
                    }
                    Text(
                        if (utme) "English Language (60 questions) plus 3 subjects of your choice (40 questions each)."
                        else "Choose up to 4 subjects and how many questions per subject.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!utme) {
                        Text("Exam type", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("", "utme", "wassce", "post-utme").forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(if (t == "") "Any" else t.uppercase()) })
                            }
                        }
                        Text("Questions per subject", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 20, 40, 60).forEach { n ->
                                FilterChip(selected = perSubject == n, onClick = { perSubject = n }, label = { Text("$n") })
                            }
                        }
                    }
                    DurationInput(hours, mins, { hours = it }, { mins = it }, timeOk)
                    Text(
                        if (utme) "Choose 3 more subjects (${chosen.size}/3)" else "Subjects (${chosen.size}/4)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (utme) {
                        val en = counts.firstOrNull { it.subject == ENGLISH }
                        SelectableRow("$ENGLISH (compulsory)", "${en?.count ?: 0}", true, null)
                    }
                }
            }
            items(counts.filter { !(utme && it.subject == ENGLISH) }) { sc ->
                val on = sc.subject in chosen
                SelectableRow(sc.subject, "${sc.count}", on) {
                    chosen = if (on) chosen - sc.subject else if (chosen.size < maxSubjects) chosen + sc.subject else chosen
                }
            }
        }
    }
}
