package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.SimklLinkStatus
import org.viptv.app.theme.Palette

@Composable
internal fun LinkedAccounts(local: Boolean, browser: Boolean, status: SimklLinkStatus?, busy: Boolean,
    error: String?, load: () -> Unit, link: () -> Unit, unlink: () -> Unit, leave: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(local, browser) { if (!local && browser) load() }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Account", style = MaterialTheme.typography.headlineMedium)
        when {
            local -> Text("Using this device locally. Simkl accounts require a server.", color = Palette.secondary)
            !browser -> Text("Link and manage accounts through your server’s web dashboard.", color = Palette.secondary)
            status == null -> if (!busy) Text("Could not load linked accounts.", color = Palette.secondary)
            !status.configured -> Text("Simkl is not configured on this server.", color = Palette.secondary)
            else -> {
                Text("Simkl", style = MaterialTheme.typography.titleLarge)
                Text(if (status.linked) "Linked to this profile" else "Link metadata and tracking for this profile", color = Palette.secondary)
                if (status.linked) OutlinedButton({ confirming = true }, enabled = !busy) { Text("Unlink Simkl") }
                else Button(link, enabled = !busy) { Text("Link Simkl") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Palette.danger) }
        TextButton(leave, enabled = !busy) { Text(if (local) "Change connection" else "Sign out") }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Unlink Simkl?") },
        text = { Text("This removes the connection for this profile. Your Simkl history is preserved.") },
        confirmButton = { TextButton({ confirming = false; unlink() }) { Text("Unlink") } },
        dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } })
}
