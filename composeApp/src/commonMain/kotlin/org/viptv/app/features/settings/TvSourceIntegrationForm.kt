package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.features.entry.TvEntryKeyboard
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvSourceIntegrationPage(schema: SourceIntegrationSchema, busy: Boolean, error: String?,
    back: () -> Unit, save: (JsonObject) -> Unit, scale: Float, profileName: String? = null, origin: String? = null) {
    val values = remember(schema.plugin_id) { mutableStateMapOf<String, String>() }
    var selected by remember(schema.plugin_id) { mutableStateOf(emptySet<String>()) }
    var editingField by remember(schema.plugin_id) { mutableStateOf(schema.credential_fields.firstOrNull()?.id) }
    val shown = remember(schema.plugin_id) { mutableStateMapOf<String, Boolean>() }
    val fieldFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(schema.plugin_id) { fieldFocus.requestFocus() }

    Column(Modifier.fillMaxSize()
        .padding(start = 96.dp * scale, end = 96.dp * scale, top = 72.dp * scale)) {
        Text("Source integrations", color = Palette.secondary, fontSize = (24 * scale).sp)
        Spacer(Modifier.height(8.dp * scale))
        Text(schema.name, fontSize = (56 * scale).sp, lineHeight = (64 * scale).sp,
            letterSpacing = (-1.12f * scale).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp * scale))
        Text(schema.provider_choices.size.let { count ->
            if (count > 0) "${sourceIntegrationRole(schema.plugin_id)} from up to ${schema.maximum_providers} providers. Applies to ${profileName?.let { "$it’s" } ?: "this"} profile only."
            else "${sourceIntegrationRole(schema.plugin_id)}. Applies to ${profileName?.let { "$it’s" } ?: "this"} profile only."
        }, color = Palette.secondary, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
        Spacer(Modifier.height(24.dp * scale))
        Row(Modifier.fillMaxWidth()) {
            org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.width(820.dp * scale).verticalScroll(rememberScrollState())) {
                schema.credential_fields.forEach { field ->
                    val active = editingField == field.id
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        Text(field.label, fontSize = (24 * scale).sp, color = Palette.text, fontWeight = FontWeight.SemiBold)
                        if (field.required) Text("Required", color = Palette.tertiary, fontSize = (20 * scale).sp)
                    }
                    Spacer(Modifier.height(10.dp * scale))
                    val requester = if (field.id == schema.credential_fields.firstOrNull()?.id) fieldFocus else remember { FocusRequester() }
                    Row(Modifier.fillMaxWidth().height(80.dp * scale)
                        .clip(RoundedCornerShape(14.dp * scale))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border((if (active) 2 else 1).dp * scale, if (active) Palette.text else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp * scale))
                        .focusRequester(requester)
                        .focusable(enabled = !busy)
                        .onFocusChanged { if (it.isFocused) editingField = field.id }
                        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { editingField = field.id }
                        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                        val raw = values[field.id].orEmpty()
                        val masked = field.secret && shown[field.id] != true
                        Text(if (raw.isEmpty()) field.label else if (masked) "•".repeat(raw.length.coerceAtMost(48)) else raw,
                            Modifier.weight(1f), fontSize = (28 * scale).sp,
                            color = if (raw.isEmpty()) Palette.secondary else Palette.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (field.secret && raw.isNotEmpty()) {
                            Text(if (shown[field.id] == true) "Hide" else "Show", color = Palette.secondary, fontSize = (22 * scale).sp,
                                modifier = Modifier.pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) {
                                    shown[field.id] = shown[field.id] != true
                                }.padding(start = 16.dp * scale))
                        }
                        if (active) Box(Modifier.padding(start = 16.dp * scale).width(2.dp * scale).height(40.dp * scale).background(Palette.accent))
                    }
                    Spacer(Modifier.height(24.dp * scale))
                    if (active && !busy) {
                        TvEntryKeyboard(scale, onDelete = { values[field.id] = values[field.id].orEmpty().dropLast(1) },
                            onClear = { values[field.id] = "" }) { value ->
                            values[field.id] = (values[field.id].orEmpty() + value).take(512)
                        }
                        Spacer(Modifier.height(24.dp * scale))
                    }
                }
                if (origin != null) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp * scale)).background(Color.White.copy(alpha = 0.06f))
                        .padding(24.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(88.dp * scale).clip(RoundedCornerShape(10.dp * scale)).background(Color.White),
                            contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Image(io.github.alexzhirkevich.qrose.rememberQrCodePainter(origin.trimEnd('/') + "/tv/"),
                                "Open this page on your phone", Modifier.size(72.dp * scale))
                        }
                        Spacer(Modifier.width(24.dp * scale))
                        Column {
                            Text("Long key? Type it on your phone", fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                            Text("or open ${origin.removePrefix("https://")}/tv on a phone or computer", color = Palette.secondary, fontSize = (20 * scale).sp)
                        }
                    }
                }
            } }
            Spacer(Modifier.width(84.dp * scale))
            org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (schema.provider_choices.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                        Text("Providers", fontSize = (32 * scale).sp, fontWeight = FontWeight.Bold)
                        Text("${selected.size} of ${schema.maximum_providers}", color = Palette.secondary, fontSize = (24 * scale).sp,
                            modifier = Modifier.padding(bottom = 2.dp * scale))
                    }
                    Spacer(Modifier.height(10.dp * scale))
                    Text("Pick up to ${schema.maximum_providers}. Leave them all off to use the default set.", color = Palette.tertiary,
                        fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
                    Spacer(Modifier.height(24.dp * scale))
                    schema.provider_choices.chunked(3).forEach { chunk ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                            chunk.forEach { provider ->
                                val chosen = provider.id in selected
                                var focused by remember { mutableStateOf(false) }
                                Row(Modifier.weight(1f).height(56.dp * scale)
                                    .onFocusChanged { focused = it.isFocused }
                                    .tvFocusOutline(focused && !busy, 10.dp * scale, scale, width = 3.dp)
                                    .clip(RoundedCornerShape(10.dp * scale))
                                    .background(if (chosen) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                                    .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy, shape = RoundedCornerShape(10.dp * scale)) {
                                        selected = if (chosen) selected - provider.id
                                            else if (selected.size < schema.maximum_providers) selected + provider.id else selected
                                    }
                                    .padding(horizontal = 14.dp * scale),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                                    Box(Modifier.size((24 * scale).dp).clip(RoundedCornerShape(6.dp * scale))
                                        .background(if (chosen) Palette.text else Color.White.copy(alpha = 0.12f))) {
                                        if (chosen) Icon(Lucide.Check, null, Modifier.size((18 * scale).dp).align(Alignment.Center), tint = Palette.background)
                                    }
                                    Text(provider.label, fontSize = (22 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(12.dp * scale))
                    }
                    Spacer(Modifier.height(20.dp * scale))
                }
                if (error != null) {
                    Text("Nothing was saved. $error", color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
                    Spacer(Modifier.height(16.dp * scale))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.height(72.dp * scale).width(280.dp * scale), scale, primary = true, enabled = !busy, keepFocus = true, activate = {
                        val configuration = buildJsonObject {
                            values.forEach { (key, value) -> if (value.isNotBlank()) put(key, value.trim()) }
                            if (schema.provider_choices.isNotEmpty()) put("providers", JsonArray(selected.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                        }
                        save(configuration)
                    }) { tint ->
                        if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp * scale), color = tint, strokeWidth = 3.dp * scale)
                        else Text("Save", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.height(72.dp * scale).width(220.dp * scale), scale, requester = cancelFocus, enabled = !busy, keepFocus = true, activate = back) { tint ->
                        Text("Cancel", color = tint, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(20.dp * scale))
                Text("Saved values are never shown again. To change one, enter a new value.", color = Palette.tertiary, fontSize = (22 * scale).sp)
            } }
        }
    }
}

@Composable
internal fun TvSourceIntegrationList(snapshot: SourceIntegrationSnapshot, busy: Boolean, error: String?,
    retry: () -> Unit, configure: (SourceIntegrationSchema) -> Unit, remove: (SourceIntegrationSchema) -> Unit,
    scale: Float, first: FocusRequester? = null, left: FocusRequester? = null, profileName: String? = null) {
    Text("Source integrations", fontSize = (40 * scale).sp, letterSpacing = (-0.8f * scale).sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(34.dp * scale))
    if (error != null) {
        Text(error, color = Palette.danger, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(12.dp * scale))
        TvAction(Modifier.height(60.dp * scale).width(200.dp * scale), scale, requester = first, enabled = !busy, keepFocus = true, activate = retry) { tint ->
            Text("Retry", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp * scale))
    }
    if (error == null && snapshot.schemas.isEmpty()) {
        Text("Unavailable on this connection.", color = Palette.secondary, fontSize = (22 * scale).sp)
        Spacer(Modifier.height(16.dp * scale))
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
        snapshot.schemas.forEachIndexed { index, schema ->
            val configured = schema.connection_id != null
            val available = schema.runtime_enabled
            val ready = available && schema.state == "Ready"
            TvSourceIntegrationCard(schema, scale, enabled = available && !busy,
                alpha = if (available) 1f else 0.55f,
                requester = if (index == 0) first else null, left = left,
                configure = { configure(schema) },
                remove = if (configured) ({ remove(schema) }) else null)
        }
    }
    Spacer(Modifier.height(28.dp * scale))
    Text("Applies to ${profileName?.let { "$it\u2019s" } ?: "this"} profile only. Saved values are never shown again. To change one, enter a new value.",
        color = Palette.tertiary, fontSize = (22 * scale).sp)
}

@Composable
private fun TvSourceIntegrationCard(schema: SourceIntegrationSchema, scale: Float, enabled: Boolean, alpha: Float,
    requester: FocusRequester?, left: FocusRequester? = null, configure: () -> Unit, remove: (() -> Unit)?) {
    val configured = schema.connection_id != null
    val available = schema.runtime_enabled
    val ready = available && schema.state == "Ready"
    Box(Modifier.fillMaxWidth().height(160.dp * scale).graphicsLayer { this.alpha = alpha }
        .clip(RoundedCornerShape(16.dp * scale))
        .background(if (available) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f))) {
        Row(Modifier.padding(start = 28.dp * scale, top = 24.dp * scale)) {
            Box(Modifier.size(64.dp * scale).clip(RoundedCornerShape(14.dp * scale)).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center) {
                Icon(sourceIntegrationIcon(schema.plugin_id), null, Modifier.size(32.dp * scale))
            }
            Spacer(Modifier.width(24.dp * scale))
            Column(Modifier.width(560.dp * scale)) {
                Text(schema.name, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp * scale))
                Text(sourceIntegrationRole(schema.plugin_id), color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = (28 + 64 + 24).dp * scale, bottom = 20.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
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
        Row(Modifier.align(Alignment.TopEnd).padding(top = 28.dp * scale, end = 28.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
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
        if (available) {
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp * scale, bottom = 16.dp * scale),
                horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                if (configured) {
                    TvAction(Modifier.height(60.dp * scale).width(210.dp * scale), scale, primary = true,
                        requester = requester, left = left, ring = false, enabled = enabled, keepFocus = true, activate = configure) { tint ->
                        Icon(Lucide.Pencil, null, Modifier.size(24.dp * scale), tint = tint)
                        Spacer(Modifier.width(12.dp * scale))
                        Text("Replace", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    remove?.let { onRemove ->
                        TvAction(Modifier.height(60.dp * scale).width(210.dp * scale), scale, left = left, ring = false, enabled = enabled, keepFocus = true, activate = onRemove) { tint ->
                            Icon(Lucide.Trash2, null, Modifier.size(24.dp * scale), tint = Palette.danger)
                            Spacer(Modifier.width(12.dp * scale))
                            Text("Remove", color = Palette.danger, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    TvAction(Modifier.height(60.dp * scale).width(240.dp * scale), scale, requester = requester,
                        left = left, ring = false, enabled = enabled, keepFocus = true, activate = configure) { tint ->
                        Icon(Lucide.SlidersHorizontal, null, Modifier.size(24.dp * scale), tint = tint)
                        Spacer(Modifier.width(12.dp * scale))
                        Text("Configure", color = tint, fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun sourceIntegrationRole(pluginId: String): String = when {
    pluginId.contains("xtream", true) -> "Live channels and guide"
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> "Live channels from a playlist"
    else -> "Movies and series"
}

private fun sourceIntegrationDetail(schema: SourceIntegrationSchema): String {
    if (!schema.runtime_enabled) return sourceIntegrationUnavailableReason(schema.plugin_id)
    if (schema.connection_id != null) {
        val label = schema.credential_fields.firstOrNull { it.required }?.label ?: "Settings"
        val key = label.replace(" API", "")
        val count = schema.saved_provider_count
        return key + " saved" + (count?.let { " · $it provider${if (it == 1) "" else "s"}" } ?: "")
    }
    val names = schema.credential_fields.map { it.label }
    return when {
        names.isEmpty() -> "No configuration required"
        names.size == 1 -> names.first()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

private fun sourceIntegrationUnavailableReason(pluginId: String): String = when {
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> "This server doesn’t accept playlists"
    pluginId.contains("xtream", true) -> "This server doesn’t accept Xtream accounts"
    else -> "This server doesn’t include this provider runtime"
}

@Composable
private fun sourceIntegrationIcon(pluginId: String) = when {
    pluginId.contains("xtream", true) -> Lucide.Tv
    pluginId.contains("m3u", true) || pluginId.contains("playlist", true) -> Lucide.List
    else -> Lucide.Puzzle
}

@Composable
internal fun TvSourceRemoveConfirmation(schema: SourceIntegrationSchema, profileName: String?, busy: Boolean,
    scale: Float, confirm: () -> Unit, dismiss: () -> Unit) {
    val initial = remember { FocusRequester() }
    val fields = schema.credential_fields.map { it.label.lowercase() }
    val what = when {
        fields.isEmpty() -> "saved settings"
        fields.size == 1 -> fields.first()
        else -> fields.dropLast(1).joinToString(", ") + " and " + fields.last()
    }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(860.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Palette.raised)
                .padding(48.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                Text("Remove ${schema.name}?", fontSize = (44 * scale).sp, lineHeight = (50 * scale).sp,
                    letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
                Text("The $what ${if (fields.size == 1) "is" else "are"} deleted from ${profileName?.let { "$it\u2019s" } ?: "this"} profile. " +
                    "Content from this provider stops showing until you set it up again. Nothing else on the account changes.",
                    fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp, color = Palette.secondary)
                Row(Modifier.padding(top = 18.dp * scale), horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.width(240.dp * scale).height(72.dp * scale), scale, primary = true, requester = initial,
                        enabled = !busy, keepFocus = true, activate = dismiss) { tint ->
                        Text("Cancel", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale, enabled = !busy, keepFocus = true, activate = confirm) {
                        Icon(Lucide.Trash2, null, Modifier.size(28.dp * scale), Palette.danger); Spacer(Modifier.width(12.dp * scale))
                        Text("Remove", color = Palette.danger, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}
