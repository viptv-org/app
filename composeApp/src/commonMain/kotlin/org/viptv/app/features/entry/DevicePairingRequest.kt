package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.viptv.app.connection.SERVER_ADDRESS_ERROR
import org.viptv.app.connection.remoteOriginOrNull
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TextButton
import org.viptv.app.features.settings.TvDialogAction
import org.viptv.app.features.settings.TvDialogField
import org.viptv.app.features.settings.TvDialogPanel
import org.viptv.app.features.settings.rememberTvDialogStop
import org.viptv.app.theme.Palette

@Composable
internal fun DevicePairingRequest(server: String, serverChanged: (String) -> Unit,
    start: (String) -> Unit, cancel: () -> Unit, back: () -> Unit,
    television: Boolean = false, scale: Float = 1f,
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
    if (editing && television) {
        val field = rememberTvDialogStop()
        val connect = rememberTvDialogStop()
        val close = { if (server.isBlank()) back() else editing = false }
        val submit = {
            val selected = remoteOriginOrNull(draft)
            if (selected == null) inputError = SERVER_ADDRESS_ERROR
            else { serverChanged(selected); revision++; editing = false }
        }
        TvDialogPanel(scale, "Server address", stops = listOf(field, connect), focus = field, dismiss = close, actions = {
            TvDialogAction(scale, "Cancel", enabled = draft.isNotBlank()) { close() }
            TvDialogAction(scale, "Connect", stop = connect, enabled = draft.isNotBlank()) { submit() }
        }) {
            TvDialogField("HTTPS server address", draft, scale, enabled = true, stop = field, invalid = inputError != null,
                change = { draft = it; inputError = null })
            inputError?.let { Text(it, Modifier.padding(top = (12 * scale).dp), color = Palette.danger,
                fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp) }
        }
        return
    }
    if (editing) AlertDialog(onDismissRequest = { if (server.isBlank()) back() else editing = false },
        title = { Text("Server address") },
        text = { AppTextField(draft, { draft = it; inputError = null },
            label = "HTTPS server address", isError = inputError != null,
            onEscape = { if (server.isBlank()) back() else editing = false },
            supportingText = { inputError?.let { Text(it) } }) },
        confirmButton = { TextButton(onClick = {
            val selected = remoteOriginOrNull(draft)
            if (selected == null) inputError = SERVER_ADDRESS_ERROR
            else { serverChanged(selected); revision++; editing = false }
        }, enabled = draft.isNotBlank()) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { if (server.isBlank()) back() else editing = false }) { Text("Cancel") } })
}
