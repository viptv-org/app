package org.viptv.app.features.entry

import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.viptv.app.connection.validatedRemoteOrigin

/** One challenge lifecycle across native layouts; changing routes cancels polling. */
@Composable
internal fun DevicePairingRequest(server: String, serverChanged: (String) -> Unit,
    start: (String) -> Unit, cancel: () -> Unit, back: () -> Unit,
    content: @Composable (newCode: () -> Unit, changeServer: () -> Unit) -> Unit) {
    var editing by remember { mutableStateOf(server.isBlank()) }
    var draft by remember(server) { mutableStateOf(server) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val latestCancel by rememberUpdatedState(cancel)
    DisposableEffect(Unit) { onDispose { latestCancel() } }
    LaunchedEffect(server, revision, editing) {
        if (!editing && server.isNotBlank()) start(server)
    }
    content({ revision++ }, { cancel(); inputError = null; editing = true })
    if (editing) AlertDialog(onDismissRequest = { if (server.isBlank()) back() else editing = false },
        title = { Text("Server address") },
        text = { OutlinedTextField(draft, { draft = it; inputError = null }, singleLine = true,
            label = { Text("HTTPS server address") }, isError = inputError != null,
            supportingText = { inputError?.let { Text(it) } }) },
        confirmButton = { TextButton(onClick = {
            val selected = runCatching { validatedRemoteOrigin(draft) }.getOrNull()
            if (selected == null) inputError = "Enter an HTTPS server address, without a page path."
            else { serverChanged(selected); revision++; editing = false }
        }, enabled = draft.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { if (server.isBlank()) back() else editing = false }) { Text("Cancel") } })
}
