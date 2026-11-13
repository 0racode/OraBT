package com.pastq.cbt.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.BuildConfig

@Composable
private fun BannerCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Shows the current update status, if there is anything to say. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val state by Updater.state.collectAsStateWithLifecycle()
    Column(modifier) {
        when (val s = state) {
            is UpdateState.Available -> BannerCard {
                Text("Update available: ${s.info.name}", style = MaterialTheme.typography.titleSmall)
                if (s.info.notes.isNotBlank()) Text(s.info.notes.lines().first(), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { Updater.startUpdate() }) { Text("Update now") }
            }
            is UpdateState.NeedsPermission -> BannerCard {
                Text("One quick step", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Allow PastQ CBT to install updates, then come back to this app. " +
                        "Nothing is lost, and the update continues by itself when you return.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { Updater.openInstallSettings() }) { Text("Open settings") }
            }
            is UpdateState.Downloading -> BannerCard {
                Text("Downloading update... ${s.percent}%", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("You can leave the app; the download keeps going.", style = MaterialTheme.typography.bodySmall)
            }
            is UpdateState.Ready -> BannerCard {
                Text("Update downloaded", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { Updater.installNow() }) { Text("Install now") }
            }
            is UpdateState.Failed -> BannerCard {
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { Updater.startUpdate() }) { Text("Try again") }
                    TextButton(onClick = { Updater.dismiss() }) { Text("Dismiss") }
                }
            }
            else -> Unit
        }
    }
}

/** Small footer: current version and a manual "check for updates" action. */
@Composable
fun VersionFooter(modifier: Modifier = Modifier) {
    val state by Updater.state.collectAsStateWithLifecycle()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { Updater.check() }, enabled = state !is UpdateState.Checking) {
            Text(if (state is UpdateState.Checking) "Checking..." else "Version ${BuildConfig.VERSION_NAME} · Check for updates")
        }
        if (state is UpdateState.UpToDate) {
            Text("You have the latest version.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
