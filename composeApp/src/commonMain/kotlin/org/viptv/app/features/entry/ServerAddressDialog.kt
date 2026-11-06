package org.viptv.app.features.entry

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import org.viptv.app.components.AppTextField
import org.viptv.app.components.TextButton
import org.viptv.app.connection.validatedRemoteOrigin
import org.viptv.app.connection.SERVER_ADDRESS_ERROR

@Composable
internal fun ServerAddressDialog(server: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(server) { mutableStateOf(server) }
    var inputError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server address") },
        text = {
            AppTextField(draft, { draft = it; inputError = null },
                label = "HTTPS server address", isError = inputError != null,
                onEscape = onDismiss,
                supportingText = { inputError?.let { Text(it) } })
        },
        confirmButton = { TextButton(onClick = {
            val selected = runCatching { validatedRemoteOrigin(draft) }.getOrNull()
            if (selected == null) inputError = SERVER_ADDRESS_ERROR
            else onConfirm(selected)
        }, enabled = draft.isNotBlank()) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
