package com.pastq.cbt.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pastq.cbt.data.Reporter
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.launch

/** Small "Report" button for one question. Opens a dialog to say what is wrong (key, option, typo...). */
@Composable
fun ReportButton(questionId: String, modifier: Modifier = Modifier) {
    var open by remember(questionId) { mutableStateOf(false) }
    var sent by remember(questionId) { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = !sent, modifier = modifier) {
        Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (sent) "Reported" else "Report")
    }
    if (open) {
        ReportDialog(questionId, onDismiss = { open = false }, onSent = { sent = true; open = false })
    }
}

@Composable
private fun ReportDialog(questionId: String, onDismiss: () -> Unit, onSent: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Report this question") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What is wrong with it?", style = MaterialTheme.typography.bodySmall)
                Reporter.reasons.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == code, role = Role.RadioButton, onClick = { reason = code }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == code, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 300) note = it },
                    label = { Text("Details (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reason != null && !busy,
                onClick = {
                    val chosen = reason ?: return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        Reporter.send(context, questionId, chosen, note.trim()).fold(
                            onSuccess = {
                                Toast.makeText(context, "Thanks. We'll review this question.", Toast.LENGTH_LONG).show()
                                onSent()
                            },
                            onFailure = { error = it.friendlyMessage(); busy = false },
                        )
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Send")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}
