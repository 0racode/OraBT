@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.Repository
import kotlinx.coroutines.launch

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
    )
}

@Composable
fun HomeScreen(repo: Repository, onPractice: () -> Unit, onExam: () -> Unit, onHistory: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var seeding by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        repo.ensureSeeded()
        seeding = false
    }
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
            }
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
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Choose a subject", style = MaterialTheme.typography.titleMedium) }
            items(counts) { sc ->
                Card(
                    Modifier.fillMaxWidth().clickable { subject = sc.subject },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (subject == sc.subject) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sc.subject)
                        Text("${sc.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (subject.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exam type", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = type == "", onClick = { type = "" }, label = { Text("Any") })
                            types.forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.uppercase()) })
                            }
                        }
                        Text("Year", style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = year == "", onClick = { year = "" }, label = { Text("Any") })
                            years.forEach { y ->
                                FilterChip(selected = year == y, onClick = { year = y }, label = { Text(y) })
                            }
                        }
                        Button(
                            onClick = {
                                Session.practiceSubject = subject
                                Session.practiceType = type
                                Session.practiceYear = year
                                onStart()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start practice") }
                        OutlinedButton(
                            enabled = !fetching,
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
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (fetching) "Fetching..." else "Fetch more questions online") }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamSetupScreen(repo: Repository, onStart: () -> Unit, onBack: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var chosen by remember { mutableStateOf(setOf<String>()) }
    var perSubject by remember { mutableStateOf(20) }
    var minutes by remember { mutableStateOf(40) }
    var type by remember { mutableStateOf("utme") }

    Scaffold(topBar = { BackBar("CBT exam", onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exam type", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("utme", "wassce", "post-utme", "").forEach { t ->
                            FilterChip(selected = type == t, onClick = { type = t }, label = { Text(if (t == "") "Any" else t.uppercase()) })
                        }
                    }
                    Text("Questions per subject", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 40).forEach { n ->
                            FilterChip(selected = perSubject == n, onClick = { perSubject = n }, label = { Text("$n") })
                        }
                    }
                    Text("Time (minutes)", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20, 40, 60, 120).forEach { m ->
                            FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m") })
                        }
                    }
                    Text("Subjects (up to 4)", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(counts) { sc ->
                val on = sc.subject in chosen
                Card(
                    Modifier.fillMaxWidth().clickable {
                        chosen = if (on) chosen - sc.subject else if (chosen.size < 4) chosen + sc.subject else chosen
                    },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (on) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sc.subject)
                        Text("${sc.count}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Button(
                    enabled = chosen.isNotEmpty(),
                    onClick = {
                        Session.examConfig = ExamConfig(
                            title = chosen.joinToString(", "),
                            subjects = chosen.toList(),
                            perSubject = perSubject,
                            minutes = minutes,
                            type = type,
                        )
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) { Text("Start exam") }
            }
        }
    }
}
