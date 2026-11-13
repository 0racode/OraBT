@file:OptIn(ExperimentalLayoutApi::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.AlocApi
import com.pastq.cbt.data.Repository
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(repo: Repository, onBack: () -> Unit) {
    val counts by repo.subjectCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var budget by remember { mutableIntStateOf(20) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun run(subjects: List<String>) {
        scope.launch {
            busy = true
            var added = 0
            var used = 0
            for (s in subjects) {
                val r = repo.syncSubject(s, budget) { done, total -> status = "$s: call $done of $total" }
                added += r.added
                used += r.calls
                if (r.error != null) {
                    status = "Stopped: ${r.error}. Saved $added new questions using $used calls."
                    busy = false
                    return@launch
                }
            }
            status = "Done. Saved $added new questions using $used API calls."
            busy = false
        }
    }

    fun updateCloud() {
        scope.launch {
            busy = true
            var added = 0
            var used = 0
            var failure: String? = null
            outer@ for (sc in counts) {
                var left = budget
                var empty = 0
                while (left > 0 && empty < 2) {
                    val n = minOf(5, left)
                    status = "${sc.subject}: asking the server for $n calls..."
                    try {
                        val r = repo.cloudFetch(sc.subject, n)
                        added += r.added
                        used += r.calls
                        left -= n
                        empty = if (r.added == 0) empty + 1 else 0
                        if (r.error != null) { failure = r.error; break@outer }
                    } catch (e: Exception) {
                        failure = e.message ?: "Could not reach the server"
                        break@outer
                    }
                }
            }
            status = "Syncing to this phone..."
            val local = try { repo.syncFromCloud() } catch (e: Exception) { 0 }
            status = (if (failure != null) "Stopped: $failure. " else "Done. ") +
                "Cloud library grew by $added questions ($used ALOC calls). $local new on this phone."
            busy = false
        }
    }

    Scaffold(topBar = { BackBar("Download questions", onBack) }) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                status = "Syncing from cloud..."
                                status = try {
                                    val n = repo.syncFromCloud { status = "Synced $it questions..." }
                                    "Cloud sync done. $n new questions on this device. No API calls used."
                                } catch (e: Exception) {
                                    "Cloud sync failed: ${e.message ?: "check your connection"}"
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sync from cloud (free, no API calls)") }
                    Text(
                        "Each API call fetches up to 40 random questions and saves them on your phone for offline use. " +
                            "Downloading stops early when no new questions are coming in.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!AlocApi.hasToken) {
                        Text(
                            "No ALOC token in this build, so downloads are disabled. Add the ALOC_TOKEN secret on GitHub and rebuild.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("API calls per subject", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 20, 50, 100).forEach { n ->
                            FilterChip(selected = budget == n, onClick = { budget = n }, label = { Text("$n") })
                        }
                    }
                    Button(
                        enabled = !busy && counts.isNotEmpty(),
                        onClick = { updateCloud() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Working..." else "Update cloud library (shared, uses ALOC calls)") }
                    Button(
                        enabled = !busy && AlocApi.hasToken && counts.isNotEmpty(),
                        onClick = { run(counts.map { it.subject }) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Downloading..." else "Download all subjects") }
                    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text("Or pick one subject", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(counts) { sc ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(sc.subject)
                            Text("${sc.count} saved", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            enabled = !busy && AlocApi.hasToken,
                            onClick = { run(listOf(sc.subject)) },
                        ) { Text("Download") }
                    }
                }
            }
        }
    }
}
