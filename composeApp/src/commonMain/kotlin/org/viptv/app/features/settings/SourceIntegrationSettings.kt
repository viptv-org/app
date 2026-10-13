package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun SourceIntegrationSettings(snapshot: SourceIntegrationSnapshot, busy: Boolean, error: String?,
    retry: () -> Unit, configure: (String, JsonObject) -> Unit, remove: (String) -> Unit) {
    var editing by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
    var deleting by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
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
    editing?.let { schema -> SourceIntegrationForm(schema, busy, error, { editing = null }) {
        configure(schema.plugin_id, it)
        editing = null
    } }
    deleting?.let { schema -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove ${schema.name}?") },
        text = { Text("This removes its saved credentials and source assignment from this profile.") },
        confirmButton = { TextButton({ schema.connection_id?.let(remove); deleting = null }, enabled = !busy) { Text("Remove") } },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
}
