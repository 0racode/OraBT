package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pastq.cbt.data.ExplainReply
import com.pastq.cbt.data.Explainer
import com.pastq.cbt.data.QuestionEntity

private sealed interface ExplainState {
    data object Idle : ExplainState
    data object Loading : ExplainState
    data class Done(val reply: ExplainReply) : ExplainState
    data class Failed(val message: String) : ExplainState
}

/** "Explain this answer" for one question. The explanation comes from the AI tutor but only for questions in the bank. */
@Composable
fun ExplanationBox(q: QuestionEntity) {
    var state by remember(q.id) {
        mutableStateOf<ExplainState>(Explainer.cached(q.id)?.let { ExplainState.Done(it) } ?: ExplainState.Idle)
    }
    var requested by remember(q.id) { mutableStateOf(0) }

    LaunchedEffect(requested) {
        if (requested == 0) return@LaunchedEffect
        state = ExplainState.Loading
        state = Explainer.fetch(q.id).fold(
            onSuccess = { ExplainState.Done(it) },
            onFailure = { ExplainState.Failed(it.message ?: "Could not get an explanation.") },
        )
    }

    when (val s = state) {
        is ExplainState.Idle -> OutlinedButton(onClick = { requested++ }) { Text("Explain this answer") }
        is ExplainState.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Thinking...")
        }
        is ExplainState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { requested++ }) { Text("Try again") }
        }
        is ExplainState.Done -> {
            val r = s.reply
            val disagrees = r.agrees == false
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (disagrees) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (disagrees) {
                        val suggested = r.ai_answer?.uppercase()
                        Text(
                            "Heads up: the AI check couldn't confirm the listed answer" +
                                (if (suggested != null) " (it suggests $suggested)" else "") +
                                ". This question is flagged for review, so treat it with care.",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(r.explanation, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (r.source == "bank") "Explanation from the question bank."
                        else "AI tutor explanation. Double-check important points with your teacher.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
