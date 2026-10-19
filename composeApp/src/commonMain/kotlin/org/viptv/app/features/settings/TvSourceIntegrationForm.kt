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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Puzzle
import com.composables.icons.lucide.SlidersHorizontal
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Tv
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
    scale: Float, first: FocusRequester? = null, left: FocusRequester? = null, profileName: String? = null) {
    // Board Tv3SetSources: heading, one-line explanation, then one 160px card per
    // integration (icon tile, name, role, saved detail, status, actions) and a
    // footer note. The card that a connection cannot use is dimmed, not hidden.
    Text("Source integrations", fontSize = (40 * scale).sp, letterSpacing = (-0.8f * scale).sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp * scale))
    Text("Where movies, series and live channels come from.", color = Palette.secondary, fontSize = (24 * scale).sp)
    Spacer(Modifier.height(34.dp * scale))
    if (error != null) {
        Text(error, color = Palette.danger, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(12.dp * scale))
        TvAction(Modifier.height(60.dp * scale).width(200.dp * scale), scale, requester = first, enabled = !busy, activate = retry) { tint ->
            Text("Retry", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp * scale))
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        snapshot.schemas.forEachIndexed { index, schema ->
            val configured = schema.connection_id != null
            val available = schema.runtime_enabled
            val ready = available && schema.state == "Ready"
            TvSourceIntegrationCard(schema, scale, enabled = available && !busy,
                alpha = if (available) 1f else 0.55f,
                requester = if (index == 0) first else null,
                configure = { configure(schema) },
                remove = if (configured) ({ remove(schema) }) else null)
        }
    }
    Spacer(Modifier.height(28.dp * scale))
    Text("Applies to ${profileName?.let { "$it\u2019s" } ?: "this"} profile only. Saved values are never shown again. To change one, enter a new value.",
        color = Palette.tertiary, fontSize = (22 * scale).sp)
}

/** One integration row, matched to board Tv3SetSources. */
@Composable
private fun TvSourceIntegrationCard(schema: SourceIntegrationSchema, scale: Float, enabled: Boolean, alpha: Float,
    requester: FocusRequester?, configure: () -> Unit, remove: (() -> Unit)?) {
    val configured = schema.connection_id != null
    val available = schema.runtime_enabled
    val ready = available && schema.state == "Ready"
    Row(Modifier.fillMaxWidth().height(160.dp * scale).graphicsLayer { this.alpha = alpha }
        .clip(RoundedCornerShape(16.dp * scale))
        .background(if (available) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f))
        .padding(horizontal = 28.dp * scale, vertical = 24.dp * scale),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.size(64.dp * scale).clip(RoundedCornerShape(14.dp * scale)).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center) {
            Icon(sourceIntegrationIcon(schema.plugin_id), null, Modifier.size(32.dp * scale))
        }
        Spacer(Modifier.width(24.dp * scale))
        Column(Modifier.weight(1f)) {
            Text(schema.name, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp * scale))
            Text(sourceIntegrationRole(schema.plugin_id), color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp * scale))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                if (configured) {
                    Row(Modifier.height(32.dp * scale).clip(RoundedCornerShape(6.dp * scale))
                        .border(1.5.dp * scale, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp * scale))
                        .padding(horizontal = 10.dp * scale), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp * scale)) {
                        Icon(Lucide.Lock, null, Modifier.size(18.dp * scale))
                        Text("Saved", fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold, color = Palette.body)
                    }
                }
                Text(sourceIntegrationDetail(schema), color = Palette.secondary, fontSize = (22 * scale).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                when {
                    ready -> Icon(Lucide.CircleCheck, null, Modifier.size(24.dp * scale), tint = Palette.body)
                    available -> Box(Modifier.size(10.dp * scale).clip(RoundedCornerShape(5.dp * scale)).background(Palette.accent))
                    else -> Icon(Lucide.Info, null, Modifier.size(24.dp * scale), tint = Palette.tertiary)
                }
                Text(
                    when {
                        ready -> "Ready"
                        available -> "Needs configuration"
                        else -> "Unavailable on this connection"
                    },
                    fontSize = (22 * scale).sp, fontWeight = FontWeight.SemiBold,
                    color = when {
                        ready -> Palette.body
                        available -> Palette.text
                        else -> Palette.tertiary
                    })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                if (available) {
                    if (configured) {
                        TvAction(Modifier.height(60.dp * scale).width(210.dp * scale), scale, primary = true,
                            requester = requester, enabled = enabled, activate = configure) { tint ->
                            Icon(Lucide.Pencil, null, Modifier.size(24.dp * scale), tint = tint)
                            Spacer(Modifier.width(12.dp * scale))
                            Text("Replace", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                        remove?.let { onRemove ->
                            TvAction(Modifier.height(60.dp * scale).width(210.dp * scale), scale, enabled = enabled, activate = onRemove) { tint ->
                                Icon(Lucide.Trash2, null, Modifier.size(24.dp * scale), tint = Palette.danger)
                                Spacer(Modifier.width(12.dp * scale))
                                Text("Remove", color = Palette.danger, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        TvAction(Modifier.height(60.dp * scale).width(240.dp * scale), scale, requester = requester,
                            enabled = enabled, activate = configure) { tint ->
                            Icon(Lucide.SlidersHorizontal, null, Modifier.size(24.dp * scale), tint = tint)
                            Spacer(Modifier.width(12.dp * scale))
                            Text("Configure", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Role copy from the boards, keyed by plugin. */
private fun sourceIntegrationRole(pluginId: String): String = when {
    pluginId.contains("xtream", true) -> "Live channels and guide"
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> "Live channels from a playlist"
    else -> "Movies and series"
}

/** The saved/needed detail line: field names when unset, a summary when set. */
private fun sourceIntegrationDetail(schema: SourceIntegrationSchema): String {
    if (schema.connection_id != null) {
        val fields = schema.credential_fields.size.takeIf { it > 0 } ?: return "Saved for this profile"
        return "${schema.provider_choices.size.coerceAtLeast(1)} provider${if (schema.provider_choices.size == 1) "" else "s"} · $fields field${if (fields == 1) "" else "s"} saved"
    }
    val names = schema.credential_fields.map { it.label }
    return when {
        names.isEmpty() -> "No configuration required"
        names.size == 1 -> names.first()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

@Composable
private fun sourceIntegrationIcon(pluginId: String) = when {
    pluginId.contains("xtream", true) -> Lucide.Tv
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> Lucide.List
    else -> Lucide.Puzzle
}
