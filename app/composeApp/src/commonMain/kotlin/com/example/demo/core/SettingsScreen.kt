package com.example.demo.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.demo.notes.SyncState

@Composable
fun SettingsScreen(
    serverUrl: String,
    isConnected: Boolean,
    isLoggedIn: Boolean,
    loginLoading: Boolean,
    loginError: String?,
    notesSyncState: SyncState,
    tasksSyncState: SyncState,
    isResettingCache: Boolean,
    isWipingServer: Boolean,
    operationMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncNotes: () -> Unit,
    onSyncTasks: () -> Unit,
    onSyncAll: () -> Unit,
    onResetLocalCache: () -> Unit,
    onWipeServerData: () -> Unit,
    onClearOperationMessage: () -> Unit
) {
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        // --- Server Connection ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server Connection", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://localhost:8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )

                Button(
                    onClick = { onServerUrlChange(urlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = urlInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isConnected) "Update" else "Connect")
                }

                if (isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Connected to server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // --- Login ---
        if (!isLoggedIn) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account Login", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Login to back up private notes and publish content.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    loginError?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            onLogin(usernameInput, passwordInput)
                            usernameInput = ""
                            passwordInput = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loginLoading && usernameInput.isNotBlank() && passwordInput.isNotBlank()
                    ) {
                        if (loginLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Login")
                        }
                    }
                }
            }
        }

        // --- Sync Controls ---
        if (isLoggedIn) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Synchronization", style = MaterialTheme.typography.titleMedium)

                    // Operation message
                    operationMessage?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.TextButton(onClick = onClearOperationMessage) {
                                Text("Dismiss")
                            }
                        }
                    }

                    // Sync all button
                    Button(
                        onClick = onSyncAll,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = notesSyncState != SyncState.SYNCING && tasksSyncState != SyncState.SYNCING
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sync Now")
                    }

                    // Individual sync statuses
                    SyncStatusRow("Notes", notesSyncState, onSyncNotes)
                    SyncStatusRow("Tasks", tasksSyncState, onSyncTasks)
                }
            }

            // --- Recovery / Maintenance ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recovery & Maintenance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Reset local cache to re-fetch PRIVATE/PUBLIC data from server. " +
                        "Wipe server data to delete all PRIVATE/PUBLIC from the remote database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reset Local Cache button
                    OutlinedButton(
                        onClick = onResetLocalCache,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isResettingCache && !isWipingServer
                    ) {
                        if (isResettingCache) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Resetting...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Local Cache")
                        }
                    }

                    // Wipe Server Data button (danger)
                    OutlinedButton(
                        onClick = onWipeServerData,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isResettingCache && !isWipingServer,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = !isResettingCache && !isWipingServer)
                            .copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
                    ) {
                        if (isWipingServer) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Wiping...")
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Wipe Server Data")
                        }
                    }
                }
            }

            // --- Logout ---
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    .copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Logout")
            }
        }

        // --- App Info ---
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Application Info", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Kotlin Multiplatform Notes App\nLocal-First Sync Engine\nLOCAL = local device only | PRIVATE/PUBLIC = server authority",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncStatusRow(label: String, state: SyncState, onSync: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label:", style = MaterialTheme.typography.bodyMedium)

        when (state) {
            SyncState.IDLE -> {
                Text("Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SyncState.SYNCING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Syncing...", style = MaterialTheme.typography.bodySmall)
                }
            }
            SyncState.ERROR -> {
                Text("Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SyncState.NEEDS_REAUTH -> {
                Text("Needs Re-login", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        OutlinedButton(onClick = onSync, enabled = state != SyncState.SYNCING) {
            Text("Sync")
        }
    }
}