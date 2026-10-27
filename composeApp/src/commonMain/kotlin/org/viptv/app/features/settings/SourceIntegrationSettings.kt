package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
internal fun SourceIntegrationSettings(snapshot: SourceIntegrationSnapshot, busy: Boolean, error: String?,
    retry: () -> Unit, configure: (String, JsonObject) -> Unit, remove: (String) -> Unit, television: Boolean = false,
    profileName: String? = null, origin: String? = null, tvScale: Float = 1f, first: androidx.compose.ui.focus.FocusRequester? = null,
    left: androidx.compose.ui.focus.FocusRequester? = null) {
    var editing by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
    var deleting by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
    if (television) {
        // The screen hands down the canvas scale (screen/1920); deriving it from
        // this pane again shrank every card below its content and clipped them.
        val schema = editing
        if (schema != null) {
            // The board's form is a full-screen page: no settings sidebar. A
            // focusable window-level popup covers the shell, like the remove dialog.
            androidx.compose.ui.window.Popup(
                onDismissRequest = { if (!busy) editing = null },
                properties = androidx.compose.ui.window.PopupProperties(focusable = true),
            ) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    TvSourceIntegrationPage(schema, busy, error, { editing = null }, { configuration ->
                        configure(schema.plugin_id, configuration); editing = null
                    }, tvScale, profileName, origin)
                }
            }
        }
        Column { TvSourceIntegrationList(snapshot, busy, error, retry, { editing = it }, { deleting = it }, tvScale,
            first = first, left = left, profileName = profileName) }
        deleting?.let { target -> TvSourceRemoveConfirmation(target, profileName, busy, tvScale,
            confirm = { target.connection_id?.let(remove); deleting = null },
            dismiss = { if (!busy) deleting = null }) }
    } else
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Source integrations", style = MaterialTheme.typography.titleLarge)
            TextButton(retry, enabled = !busy) { Text("Refresh") }
        }
        Text("Configuration applies to this profile. Saved credentials are never displayed.", color = Palette.secondary)
        if (error != null) {
            Text(error, color = Palette.danger)
            TextButton(retry, enabled = !busy) { Text("Retry") }
        }
        snapshot.schemas.forEach { schema ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(schema.name, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    !schema.runtime_enabled -> "Unavailable on this connection"
                    schema.state == "Ready" -> "Configured for this profile"
                    else -> "Needs configuration"
                }, color = Palette.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ editing = schema }, enabled = schema.runtime_enabled && !busy) {
                        Text(if (schema.connection_id == null) "Configure" else "Replace configuration")
                    }
                    if (schema.connection_id != null) TextButton({ deleting = schema }, enabled = !busy) { Text("Remove") }
                }
            }
        }
    }
    if (!television) editing?.let { schema ->
        val dismiss = { editing = null }
        val save: (JsonObject) -> Unit = { configuration -> configure(schema.plugin_id, configuration); editing = null }
        SourceIntegrationForm(schema, busy, error, dismiss, save = save)
    }
    if (!television) deleting?.let { schema -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove ${schema.name}?") },
        text = { Text("This removes its saved credentials and source assignment from this profile.") },
        confirmButton = { TextButton({ schema.connection_id?.let(remove); deleting = null }, enabled = !busy) { Text("Remove") } },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
}
