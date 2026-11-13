@file:OptIn(ExperimentalMaterial3Api::class)

package com.pastq.cbt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pastq.cbt.data.AuthManager
import com.pastq.cbt.data.LeaderRow
import com.pastq.cbt.data.Repository
import com.pastq.cbt.data.friendlyMessage
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val account by AuthManager.account.collectAsStateWithLifecycle()
    if (account == null) SignedOutContent(onBack) else SignedInContent(account!!, onBack)
}

@Composable
private fun SignedOutContent(onBack: () -> Unit) {
    var creating by remember { mutableStateOf(true) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = !busy && email.contains("@") && password.length >= 6

    Scaffold(
        topBar = { BackBar(if (creating) "Create account" else "Log in", onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = canSubmit,
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                val result = if (creating) AuthManager.signUp(email.trim(), password, first, last)
                                else AuthManager.signIn(email.trim(), password)
                                busy = false
                                if (result == null) onBack() else error = result
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "Please wait..." else if (creating) "Create account" else "Log in") }
                    TextButton(onClick = { creating = !creating; error = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (creating) "I already have an account" else "Create a new account")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "An account is optional. It puts your best scores on the daily, weekly and monthly leaderboards.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (creating) {
                OutlinedTextField(first, { first = it.take(40) }, label = { Text("First name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(last, { last = it.take(40) }, label = { Text("Last name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text("Password (at least 6 characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (creating) {
                Text(
                    "Leaderboard names show your first name and last initial. With no name, you appear as \"Student\" plus a short code.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SignedInContent(account: AuthManager.Account, onBack: () -> Unit) {
    var first by remember(account) { mutableStateOf(account.firstName) }
    var last by remember(account) { mutableStateOf(account.lastName) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("Your account and all your leaderboard scores will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val err = AuthManager.deleteAccount()
                        if (err == null) onBack() else message = err
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(topBar = { BackBar("Profile", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(account.displayName, style = MaterialTheme.typography.titleLarge)
            Text(account.email, style = MaterialTheme.typography.bodyMedium)

            Text("Name on the leaderboard", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(first, { first = it.take(40) }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(last, { last = it.take(40) }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = AuthManager.updateProfile(first, last) ?: "Profile saved."
                        busy = false
                    }
                },
            ) { Text("Save name") }

            Text("Change password", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                oldPw, { oldPw = it }, label = { Text("Current password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                newPw, { newPw = it }, label = { Text("New password (6+ characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                enabled = !busy && oldPw.isNotEmpty() && newPw.length >= 6,
                onClick = {
                    scope.launch {
                        busy = true
                        message = AuthManager.changePassword(oldPw, newPw) ?: "Password changed."
                        if (message == "Password changed.") { oldPw = ""; newPw = "" }
                        busy = false
                    }
                },
            ) { Text("Change password") }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { scope.launch { AuthManager.signOut(); onBack() } }) { Text("Sign out") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun LeaderboardScreen(repo: Repository, onBack: () -> Unit) {
    val periods = listOf("day" to "Today", "week" to "This week", "month" to "This month")
    var period by remember { mutableStateOf("day") }
    var rows by remember { mutableStateOf<List<LeaderRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(period, reload) {
        rows = null; error = null
        try {
            rows = repo.leaderboard(period)
        } catch (e: Exception) {
            error = e.friendlyMessage()
        }
    }

    Scaffold(topBar = { BackBar("Leaderboard", onBack) }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { (key, label) ->
                    FilterChip(selected = period == key, onClick = { period = key }, label = { Text(label) })
                }
            }
            Text(
                "Each person's best score. Exams of 20+ questions count. Resets daily, weekly and monthly (Nigerian time).",
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { reload++ }) { Text("Try again") }
                }
                rows == null -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
                rows!!.isEmpty() -> Text("No scores yet for this period. Take an exam while signed in to be the first.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(rows!!) { _, r ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (r.rank <= 3) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${r.rank}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Column {
                                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${r.correct}/${r.total} · ${r.mode} · ${formatTime(r.seconds)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                Text("${"%.1f".format(r.pct)}%", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
