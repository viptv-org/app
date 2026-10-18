package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.components.TvAction
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.features.entry.TvEntryKeyboard
import org.viptv.app.theme.Palette

/**
 * TV source-integration configuration as a full-screen settings page (the same
 * pattern as the TV sign-in fields): heading, entry field with the TV
 * keyboard, focusable provider rows, Save and Cancel. No dialogs; Back and
 * Cancel both return to the integration list.
 */
@Composable
internal fun TvSourceIntegrationPage(schema: SourceIntegrationSchema, busy: Boolean, error: String?,
    back: () -> Unit, save: (JsonObject) -> Unit, scale: Float) {
    val values = remember(schema.plugin_id) { mutableStateMapOf<String, String>() }
    var selected by remember(schema.plugin_id) { mutableStateOf(emptySet<String>()) }
    var editingField by remember(schema.plugin_id) { mutableStateOf(schema.credential_fields.firstOrNull()?.id) }
    val fieldFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(schema.plugin_id) { fieldFocus.requestFocus() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 96.dp * scale, end = 96.dp * scale, top = 96.dp * scale, bottom = 64.dp * scale)) {
        Text("Configure ${schema.name}", fontSize = (44 * scale).sp, lineHeight = (52 * scale).sp,
            letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp * scale))
        Text(when {
                schema.connection_id != null -> "Replace the saved settings for this profile."
                else -> "Enter the settings below. Values stay on this TV."
            }, color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
        Spacer(Modifier.height(32.dp * scale))
        schema.credential_fields.forEach { field ->
            Text(field.label, fontSize = (24 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp * scale))
            val active = editingField == field.id
            val requester = if (field.id == schema.credential_fields.firstOrNull()?.id) fieldFocus else remember { FocusRequester() }
            Row(Modifier.fillMaxWidth(0.62f).height(84.dp * scale)
                .clip(RoundedCornerShape(14.dp * scale))
                .background(Color.White.copy(alpha = 0.08f))
                .border((if (active) 2 else 1).dp * scale, if (active) Palette.text else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp * scale))
                .focusRequester(requester)
                .focusable(enabled = !busy)
                .onFocusChanged { if (it.isFocused) editingField = field.id }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { editingField = field.id }
                .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                Text(values[field.id].orEmpty().ifEmpty { field.label }, Modifier.weight(1f),
                    fontSize = (30 * scale).sp, color = if (values[field.id].isNullOrEmpty()) Palette.secondary else Palette.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) Box(Modifier.width(2.dp * scale).height(40.dp * scale).background(Palette.accent))
            }
            Spacer(Modifier.height(20.dp * scale))
            if (active && !busy) {
                TvEntryKeyboard(scale) { value ->
                    values[field.id] = (values[field.id].orEmpty() + value).take(512)
                }
                Spacer(Modifier.height(20.dp * scale))
            }
        }
        if (schema.provider_choices.isNotEmpty()) {
            Text("Providers", fontSize = (24 * scale).sp, color = Palette.secondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp * scale))
            Text("Leave empty to use the default selection.", color = Palette.tertiary, fontSize = (20 * scale).sp)
            Spacer(Modifier.height(14.dp * scale))
            schema.provider_choices.chunked(4).forEach { chunk ->
                Row(Modifier.fillMaxWidth(0.62f), horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                    chunk.forEach { provider ->
                        val chosen = provider.id in selected
                        Row(Modifier.weight(1f).height(64.dp * scale)
                            .clip(RoundedCornerShape(10.dp * scale))
                            .background(if (chosen) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                            .focusable(enabled = !busy)
                            .onFocusChanged { }
                            .clickable(enabled = !busy) {
                                selected = if (chosen) selected - provider.id
                                    else if (selected.size < schema.maximum_providers) selected + provider.id else selected
                            }
                            .padding(horizontal = 14.dp * scale),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                            Box(Modifier.size((22 * scale).dp).clip(RoundedCornerShape(4.dp * scale))
                                .background(if (chosen) Palette.text else Color.White.copy(alpha = 0.12f))) {
                                if (chosen) Icon(Lucide.Check, null, Modifier.size((16 * scale).dp).align(Alignment.Center), tint = Palette.background)
                            }
                            Text(provider.label, fontSize = (20 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp * scale))
            }
            Spacer(Modifier.height(10.dp * scale))
        }
        error?.let { Text(it, color = Palette.danger, fontSize = (22 * scale).sp); Spacer(Modifier.height(16.dp * scale)) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, primary = true, enabled = !busy, activate = {
                val configuration = buildJsonObject {
                    values.forEach { (key, value) -> if (value.isNotBlank()) put(key, value.trim()) }
                    if (schema.provider_choices.isNotEmpty()) put("providers", JsonArray(selected.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                }
                save(configuration)
            }) { tint -> Text("Save", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold) }
            TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, requester = cancelFocus, enabled = !busy, activate = back) { tint ->
                Text("Cancel", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** TV settings rows for each integration, following the settings section pattern. */
@Composable
internal fun TvSourceIntegrationList(snapshot: SourceIntegrationSnapshot, busy: Boolean, error: String?,
    retry: () -> Unit, configure: (SourceIntegrationSchema) -> Unit, remove: (SourceIntegrationSchema) -> Unit,
    scale: Float, first: FocusRequester? = null, left: FocusRequester? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        if (error != null) {
            Text(error, color = Palette.danger, fontSize = (22 * scale).sp)
            TvAction(Modifier.height(56.dp * scale).width(200.dp * scale), scale, requester = first, enabled = !busy, activate = retry) { tint ->
                Text("Retry", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp * scale))
        }
        Text("Configuration applies to this profile. Saved credentials are never displayed.",
            color = Palette.secondary, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(6.dp * scale))
        snapshot.schemas.forEach { schema ->
            val state = when {
                !schema.runtime_enabled -> "Unavailable on this connection"
                schema.state == "Ready" -> "Configured for this profile"
                else -> "Needs configuration"
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 96.dp * scale)
                .clip(RoundedCornerShape(14.dp * scale))
                .background(Color.White.copy(alpha = 0.07f))
                .focusable()
                .onFocusChanged { }
                .padding(horizontal = 24.dp * scale, vertical = 16.dp * scale),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                Column(Modifier.weight(1f)) {
                    Text(schema.name, fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold)
                    Text(state, fontSize = (20 * scale).sp, color = Palette.secondary)
                }
                TvAction(Modifier.height(56.dp * scale).width(220.dp * scale), scale,
                    requester = if (schema === snapshot.schemas.firstOrNull()) first else null,
                    enabled = schema.runtime_enabled && !busy, activate = { configure(schema) }) { tint ->
                    Text(if (schema.connection_id == null) "Configure" else "Replace", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
                }
                if (schema.connection_id != null) TvAction(Modifier.height(56.dp * scale).width(180.dp * scale), scale, enabled = !busy, activate = { remove(schema) }) { tint ->
                    Text("Remove", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
