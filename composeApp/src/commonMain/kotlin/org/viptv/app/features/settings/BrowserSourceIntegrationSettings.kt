package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Puzzle
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Tv
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.TextButton

@Composable
internal fun BrowserSourceIntegrationSettings(snapshot: SourceIntegrationSnapshot, busy: Boolean, error: String?,
    retry: () -> Unit, profileName: String?, configure: (String, JsonObject) -> Unit, remove: (String) -> Unit) {
    var editing by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
    var deleting by remember { mutableStateOf<SourceIntegrationSchema?>(null) }
    org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Source integrations", fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-.48).sp, fontWeight = FontWeight.Bold)
        Text("Where movies, series and live channels come from.", fontSize = 14.sp, lineHeight = 20.sp, color = Palette.secondary)
        if (busy) Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.accent.copy(alpha = .4f)))
        error?.let {
            Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.danger)
            BrowserSourceAction("Retry", primary = true, enabled = !busy, onClick = retry)
        }
        snapshot.schemas.forEach { schema ->
            BrowserIntegrationCard(schema, busy, configure = { editing = it }, remove = { deleting = it })
        }
        Text("Applies to ${profileName?.let { "$it\u2019s" } ?: "this"} profile only. Saved values are never shown again. To change one, enter a new value.",
            fontSize = 13.sp, lineHeight = 19.sp, color = Palette.tertiary)
    } }
    editing?.let { schema ->
        SourceIntegrationForm(schema, busy, error, { editing = null }, save = { configuration ->
            configure(schema.plugin_id, configuration)
            editing = null
        })
    }
    deleting?.let { schema ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remove ${schema.name}?") },
            text = { Text("This removes its saved credentials and source assignment from this profile.") },
            confirmButton = {
                TextButton({ schema.connection_id?.let(remove); deleting = null }, enabled = !busy) { Text("Remove") }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun BrowserIntegrationCard(schema: SourceIntegrationSchema, busy: Boolean,
    configure: (SourceIntegrationSchema) -> Unit, remove: (SourceIntegrationSchema) -> Unit) {
    val configured = schema.connection_id != null
    val available = schema.runtime_enabled
    val ready = available && schema.state == "Ready"
    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if (available) 1f else .55f }
        .clip(RoundedCornerShape(14.dp))
        .background(Color.White.copy(alpha = if (available) .06f else .03f))
        .padding(14.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .1f)),
            contentAlignment = Alignment.Center) {
            Icon(browserSourceIcon(schema.plugin_id), null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(schema.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(browserSourceRole(schema.plugin_id), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                when {
                    ready -> Icon(Lucide.CircleCheck, null, Modifier.size(14.dp), tint = Palette.body)
                    available -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Palette.accent))
                    else -> Icon(Lucide.Info, null, Modifier.size(14.dp), tint = Palette.tertiary)
                }
                Text(when { ready -> "Ready"; available -> "Needs configuration"; else -> "Unavailable on this connection" },
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = when { ready -> Palette.body; available -> Palette.text; else -> Palette.tertiary })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(browserSourceDetail(schema), fontSize = 12.sp, color = Palette.secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (configured) Row(Modifier.height(19.dp).clip(RoundedCornerShape(5.dp))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.body)
                }
            }
            if (available) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (configured) {
                    BrowserSourceAction("Replace", primary = false, enabled = !busy, icon = Lucide.Pencil) { configure(schema) }
                    BrowserSourceAction("Remove", primary = false, enabled = !busy, icon = Lucide.Trash2, destructive = true) { remove(schema) }
                } else BrowserSourceAction("Configure", primary = true, enabled = !busy, icon = Lucide.SlidersHorizontal) { configure(schema) }
            }
        }
    }
}

@Composable
private fun BrowserSourceAction(label: String, primary: Boolean, enabled: Boolean, icon: ImageVector? = null,
    destructive: Boolean = false, onClick: () -> Unit) {
    val background = when {
        primary -> Palette.text
        destructive -> Palette.danger.copy(alpha = .14f)
        else -> Color.White.copy(alpha = .12f)
    }
    val content = when { primary -> Palette.background; destructive -> Palette.danger; else -> Palette.text }
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(10.dp))
        .background(if (enabled) background else background.copy(alpha = .05f))
        .pointerClickable(enabled = enabled, onClick = onClick, shape = RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon?.let { Icon(it, null, Modifier.size(14.dp), tint = content) }
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = content)
    }
}

private fun browserSourceRole(pluginId: String): String = when {
    pluginId.contains("xtream", true) -> "Live channels and guide"
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> "Live channels from a playlist"
    else -> "Movies and series"
}

private fun browserSourceDetail(schema: SourceIntegrationSchema): String {
    if (schema.connection_id != null) {
        val fields = schema.credential_fields.size.takeIf { it > 0 } ?: return "Saved for this profile"
        val providers = schema.provider_choices.size
        val providerText = if (providers > 0) "$providers provider${if (providers == 1) "" else "s"} · " else ""
        return "${providerText}$fields field${if (fields == 1) "" else "s"} saved"
    }
    val names = schema.credential_fields.map { it.label }
    return when {
        names.isEmpty() -> "No configuration required"
        names.size == 1 -> names.first()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

@Composable
private fun browserSourceIcon(pluginId: String) = when {
    pluginId.contains("xtream", true) -> Lucide.Tv
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> Lucide.List
    else -> Lucide.Puzzle
}
