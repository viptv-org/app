package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import org.viptv.app.components.pageBottomClearance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Button
import org.viptv.app.components.OutlinedButton
import org.viptv.app.components.TextButton
import org.viptv.app.connection.SimklLinkStatus
import org.viptv.app.theme.Palette

@Composable
internal fun LinkedServicesSettings(status: SimklLinkStatus?, profileName: String?, compact: Boolean, busy: Boolean,
    error: String?, load: () -> Unit, link: () -> Unit, unlink: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var awaitingBrowser by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(windowFocused) {
        if (windowFocused && awaitingBrowser) {
            awaitingBrowser = false
            load()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 8.dp).pageBottomClearance(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Linked services", Modifier.padding(top = 16.dp), fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Bold)
        profileName?.let { Text("For $it", color = Palette.secondary, fontSize = 13.sp) }
        Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Simkl", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(linkedServicesNote(status, busy), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
                            color = Palette.secondary)
                    }
                }
                if (status?.linked == true) {
                    HorizontalDivider(color = Palette.raised, thickness = 1.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedButton({ confirming = true }, enabled = !busy) { Text("Unlink Simkl") }
                    }
                } else if (status?.configured == true) {
                    HorizontalDivider(color = Palette.raised, thickness = 1.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button({ awaitingBrowser = true; link() }, enabled = !busy) { Text("Link Simkl") }
                    }
                }
            }
        }
        TextButton(load, enabled = !busy) { Text(if (error != null || status == null) "Retry" else "Refresh") }
        error?.let { Text(it, color = Palette.danger) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (confirming) AlertDialog(onDismissRequest = { if (!busy) confirming = false }, title = { Text("Unlink Simkl?") },
        text = { Text("This removes the connection for this profile. Your Simkl history is preserved.") },
        confirmButton = { TextButton({ confirming = false; unlink() }, enabled = !busy) { Text("Unlink", color = Palette.danger) } },
        dismissButton = { TextButton({ confirming = false }, enabled = !busy) { Text("Cancel") } })
}

private fun linkedServicesNote(status: SimklLinkStatus?, busy: Boolean): String = when {
    status == null -> if (busy) "Loading…" else "Could not load linked services."
    !status.configured -> "Simkl is not configured on this server."
    status.linked -> "Linked to this profile"
    else -> "Link metadata and tracking for this profile"
}