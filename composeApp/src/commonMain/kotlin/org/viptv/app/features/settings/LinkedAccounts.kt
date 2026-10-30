package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.SimklLinkStatus
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton

@Composable
internal fun LinkedAccounts(browser: Boolean, status: SimklLinkStatus?, busy: Boolean, link: () -> Unit, unlink: () -> Unit,
    dashboardAddress: String? = null) {
    var confirming by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
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
        if (!browser && dashboardAddress != null) ServerWebAction("Open server dashboard", dashboardAddress)
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("Unlink Simkl?") },
        text = { Text("This removes the connection for this profile. Your Simkl history is preserved.") },
        confirmButton = { TextButton({ confirming = false; unlink() }) { Text("Unlink") } },
        dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } })
}
