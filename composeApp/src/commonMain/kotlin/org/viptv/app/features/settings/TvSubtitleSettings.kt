package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette

@Composable
internal fun TvSubtitleSettings(prefs: TvSettingsPreferences?, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    change: (TvSettingsPreferences) -> Unit, choose: (String, List<String>, (String) -> Unit) -> Unit, unavailable: () -> Unit, actualPreferencesOnly: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(56.dp * scale)) {
        TvSettingsHeading("Subtitles & audio", scale)
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp * scale)) {
            Column(Modifier.width(720.dp * scale)) {
                Box(Modifier.fillMaxWidth().height(405.dp * scale).clip(RoundedCornerShape(14.dp * scale))) {
                    AsyncImage(Res.getUri("files/entry/subtitle-preview.jpg"), "Subtitle style preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (prefs != null) {
                        val font = when (prefs.subtitleSize) { "Small" -> 26; "Medium" -> 32; "Large" -> 40; "Extra large" -> 48;
                            else -> ((prefs.subtitleSize.removeSuffix("×").toDoubleOrNull() ?: 1.0) * 32).toInt().coerceIn(16, 64) }
                        val background = prefs.subtitleBackgroundColor.takeUnless { it == "#00000000" } ?: "#B3000000"
                        val backgroundColor = background.removePrefix("#").toLongOrNull(16)?.let { if (background.length == 7) it or 0xFF000000 else it } ?: 0xB3000000
                        Text("I didn’t hear you come in.", Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp * scale)
                            .background(if (prefs.subtitleBackground == "Box") Color(backgroundColor) else Color.Transparent).padding(horizontal = 12.dp * scale),
                            color = Color(prefs.subtitleColor), fontSize = (font * scale).sp, lineHeight = ((font + 8) * scale).sp, fontWeight = FontWeight.SemiBold,
                            style = TextStyle(shadow = if (prefs.subtitleBackground == "Shadow") Shadow(Color.Black, Offset(2f, 2f), 4f) else null))
                    }
                }
                Spacer(Modifier.height(45.dp * scale))
                TvSubtitleChoiceRow("Size", listOf("Small", "Medium", "Large", "Extra large"), prefs?.subtitleSize, scale, initial, sidebar) {
                    if (prefs != null) change(prefs.copy(subtitleSize = it)) else unavailable() }
                Spacer(Modifier.height(24.dp * scale))
                TvSubtitleChoiceRow("Background", if (actualPreferencesOnly) listOf("None", "Box") else listOf("None", "Shadow", "Box"), prefs?.subtitleBackground, scale, null, sidebar) {
                    if (prefs != null) change(prefs.copy(subtitleBackground = it)) else unavailable() }
                Spacer(Modifier.height(24.dp * scale))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
                    Text("Text colour", Modifier.width(210.dp * scale), fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
                    listOf("White" to 0xFFFFFFFF, "Yellow" to 0xFFFFE16B, "Blue" to 0xFF7AE0EC, "Green" to 0xFFB5EF98).forEachIndexed { index, (label, value) ->
                        TvSubtitleColor(value, label, prefs?.subtitleColor == value, scale, if (index == 0) sidebar else null) {
                            if (prefs != null) change(prefs.copy(subtitleColor = value)) else unavailable() }
                    }
                }
            }
            Column(Modifier.width(424.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                TvSettingsRow("Subtitle language", scale = scale, value = prefs?.subtitleLanguage ?: "Unavailable", left = sidebar) {
                    if (prefs != null) choose("Subtitle language", listOf("Default", "English", "Spanish", "Portuguese", "French", "German")) { change(prefs.copy(subtitleLanguage = it)) } else unavailable() }
                TvSettingsRow("Audio language", scale = scale, value = prefs?.audioLanguage ?: "Unavailable", left = sidebar) {
                    if (prefs != null) choose("Audio language", listOf("Original", "English", "Spanish", "French", "German")) { change(prefs.copy(audioLanguage = it)) } else unavailable() }
                Text(if (prefs != null) "The preview updates as you change a setting. Saved preferences apply to the next playback." else "Subtitle preferences are not available on this connection yet.",
                    Modifier.padding(top = 12.dp * scale), fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Color(0xFF8F8D89))
            }
        }
    }
}

@Composable
private fun TvSubtitleChoiceRow(label: String, values: List<String>, selected: String?, scale: Float, initial: FocusRequester?,
    sidebar: FocusRequester, select: (String) -> Unit) {
    Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        Text(label, Modifier.width(212.dp * scale), fontSize = (26 * scale).sp, fontWeight = FontWeight.SemiBold)
        values.forEachIndexed { index, value -> TvAction(Modifier.height(60.dp * scale).focusProperties { if (index == 0) left = sidebar }, scale,
            requester = if (value == selected || selected == null && index == 0) initial else null, cornerRadius = 30.dp * scale,
            background = if (value == selected) Color(0xFF333333) else Color.Transparent,
            contentPadding = PaddingValues(horizontal = 24.dp * scale), activate = { select(value) }) {
            if (value == selected) { Icon(Lucide.Check, null, Modifier.size(22.dp * scale), it); Spacer(Modifier.width(8.dp * scale)) }
            Text(value, color = if (value == selected || it == Palette.background) it else Palette.secondary,
                fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
        } }
    }
}
