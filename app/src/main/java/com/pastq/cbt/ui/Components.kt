package com.pastq.cbt.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pastq.cbt.data.QuestionEntity

/**
 * Shows one question with four options.
 * [showAnswer] reveals correct/wrong colouring (practice mode); in exam mode it is false.
 */
@Composable
fun QuestionCard(
    q: QuestionEntity,
    picked: Char?,
    showAnswer: Boolean,
    onPick: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val correct = q.answer.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val meta = listOf(q.subject, q.type.uppercase(), q.year).filter { it.isNotBlank() }.joinToString(" · ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(meta, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ReportButton(q.id)
        }
        if (q.section.isNotBlank()) {
            HtmlText(q.section, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HtmlText(q.question, style = MaterialTheme.typography.titleMedium)
        QuestionImage(q.image)

        listOf('a', 'b', 'c', 'd', 'e').forEach { letter ->
            val text = q.optionText(letter)
            if (text.isBlank()) return@forEach
            val isPicked = picked == letter
            val container = when {
                showAnswer && picked != null && letter == correct -> MaterialTheme.colorScheme.tertiaryContainer
                showAnswer && isPicked && letter != correct -> MaterialTheme.colorScheme.errorContainer
                isPicked -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = container),
                border = if (isPicked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isPicked,
                        enabled = !(showAnswer && picked != null),
                        role = Role.RadioButton,
                        onClick = { onPick(letter) },
                    ),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isPicked, onClick = null, modifier = Modifier.padding(8.dp))
                    HtmlText(text, prefix = "${letter.uppercaseChar()}.  ", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (showAnswer && picked != null) {
            Text(
                "Correct answer: ${correct?.uppercaseChar()}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (q.solution.isNotBlank()) HtmlText(q.solution, style = MaterialTheme.typography.bodyMedium)
            if (q.solution.trim().length < 20) ExplanationBox(q)
        }
    }
}
