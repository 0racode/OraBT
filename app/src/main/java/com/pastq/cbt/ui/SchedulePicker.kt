package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Helpers for scheduled live tests. The server works in UTC; the admin picks the time on the phone's own clock.
// (java.time is avoided because the app supports Android 7.)

private const val ISO_UTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

/** Milliseconds -> the ISO time the server expects, for example 2026-11-20T19:30:00.000Z. */
fun isoUtc(millis: Long): String =
    SimpleDateFormat(ISO_UTC, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(millis))

/** The ISO time the server sends back -> milliseconds, or null when missing or not readable. */
fun parseIsoUtc(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        SimpleDateFormat(ISO_UTC, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time
    }.getOrNull()
}

/** "Fri 20 Nov, 3:30 PM" on this phone's clock. */
fun formatWhen(millis: Long): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(java.util.Date(millis))

private fun formatDate(millis: Long): String =
    SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(java.util.Date(millis))

private fun formatClock(millis: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(millis))

/** "in 2d 3h", "in 1:05:00" or "in 04:30" for a countdown shown on a card. */
fun countdownLabel(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return if (s >= 86400) "${s / 86400}d ${(s % 86400) / 3600}h" else formatTime(s)
}

/** A sensible first suggestion: 30 minutes from now, rounded up to the next 5 minutes. */
fun defaultStartMillis(): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis() + 30 * 60_000L
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val m = cal.get(Calendar.MINUTE)
    val extra = (5 - m % 5) % 5
    cal.add(Calendar.MINUTE, extra)
    return cal.timeInMillis
}

// The date picker reports the chosen day as midnight UTC, so the year, month and day are read in UTC
// and then applied to the phone's own calendar.
private fun utcMidnightOfLocalDay(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun withLocalDate(oldMillis: Long, pickedUtcDay: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcDay }
    return Calendar.getInstance().apply {
        timeInMillis = oldMillis
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun withLocalTime(oldMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = oldMillis
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Two buttons, one for the date and one for the time of day. Tapping either opens the matching picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartTimeField(millis: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(formatDate(millis)) }
        OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) { Text(formatClock(millis)) }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOfLocalDay(millis))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(withLocalDate(millis, it)) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = android.text.format.DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { onChange(withLocalTime(millis, state.hour, state.minute)); showTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
