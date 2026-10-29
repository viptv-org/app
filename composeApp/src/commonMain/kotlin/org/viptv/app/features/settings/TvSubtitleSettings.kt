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

private val tvSubtitleColours = listOf("White" to 0xFFFFFFFF, "Yellow" to 0xFFFFE16B, "Blue" to 0xFF7AE0EC, "Green" to 0xFFB5EF98)

internal val tvSubtitleRows = listOf("Audio language", "Subtitles", "Subtitle language", "Subtitle size", "Subtitle background", "Subtitle colour")

internal fun tvSubtitleColourLabel(value: Long): String = tvSubtitleColours.firstOrNull { it.second == value }?.first ?: "Custom"

@Composable
internal fun TvSubtitleSettings(prefs: TvSettingsPreferences?, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    change: (TvSettingsPreferences) -> Unit, choose: (String, List<String>, (String) -> Unit) -> Unit, unavailable: () -> Unit, actualPreferencesOnly: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(40.dp * scale)) {
        TvSettingsHeading("Subtitles & audio", scale)
        Row(horizontalArrangement = Arrangement.spacedBy(48.dp * scale)) {
            Column(Modifier.width(560.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                tvSubtitleRows.forEachIndexed { index, row ->
                    val requester = if (index == 0) initial else null
                    fun pick(values: List<String>, apply: (TvSettingsPreferences, String) -> TvSettingsPreferences) {
                        if (prefs != null) choose(row, values) { change(apply(prefs, it)) } else unavailable()
                    }
                    when (row) {
                        "Audio language" -> TvSettingsRow(row, scale = scale, value = prefs?.audioLanguage ?: "Unavailable", requester = requester, left = sidebar) {
                            pick(listOf("Original language", "English", "Spanish", "French", "German")) { p, v -> p.copy(audioLanguage = v) } }
                        "Subtitles" -> TvSettingsRow(row, scale = scale, value = prefs?.let { subtitleModeLabel(it.subtitleMode, it.audioLanguageTag()) } ?: "Unavailable",
                            requester = requester, left = sidebar) {
                            pick(subtitleModeChoices(prefs?.audioLanguageTag()).map { it.second }) { p, v -> p.copy(subtitleMode = subtitleModeValue(v)) } }
                        "Subtitle language" -> TvSettingsRow(row, scale = scale, value = prefs?.subtitleLanguage ?: "Unavailable", requester = requester, left = sidebar) {
                            pick(listOf("Default", "English", "Spanish", "Portuguese", "French", "German")) { p, v -> p.copy(subtitleLanguage = v) } }
                        "Subtitle size" -> TvSettingsRow(row, scale = scale, value = prefs?.subtitleSize ?: "Unavailable", requester = requester, left = sidebar) {
                            pick(listOf("Small", "Medium", "Large", "Extra large")) { p, v -> p.copy(subtitleSize = v) } }
                        "Subtitle background" -> TvSettingsRow(row, scale = scale, value = prefs?.subtitleBackground ?: "Unavailable", requester = requester, left = sidebar) {
                            pick(if (actualPreferencesOnly) listOf("None", "Box") else listOf("None", "Shadow", "Box")) { p, v -> p.copy(subtitleBackground = v) } }
                        "Subtitle colour" -> TvSettingsRow(row, scale = scale, value = prefs?.let { tvSubtitleColourLabel(it.subtitleColor) } ?: "Unavailable",
                            requester = requester, left = sidebar) {
                            pick(tvSubtitleColours.map { it.first }) { p, v -> p.copy(subtitleColor = tvSubtitleColours.first { it.first == v }.second) } }
                    }
                }
                if (prefs == null) Text("Subtitle preferences are not available on this connection yet.",
                    Modifier.padding(top = 12.dp * scale), fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Color(0xFF8F8D89))
            }
            TvSubtitlePreview(prefs, scale)
        }
    }
}

@Composable
private fun TvSubtitlePreview(prefs: TvSettingsPreferences?, scale: Float) {
    val ratio = 576f / 720f
    Box(Modifier.width(576.dp * scale).height(324.dp * scale).clip(RoundedCornerShape(14.dp * scale)).focusProperties { canFocus = false }) {
        AsyncImage(Res.getUri("files/entry/subtitle-preview.jpg"), "Subtitle style preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (prefs != null) {
            val font = when (prefs.subtitleSize) { "Small" -> 26; "Medium" -> 32; "Large" -> 40; "Extra large" -> 48;
                else -> ((prefs.subtitleSize.removeSuffix("×").toDoubleOrNull() ?: 1.0) * 32).toInt().coerceIn(16, 64) } * ratio
            val background = prefs.subtitleBackgroundColor.takeUnless { it == "#00000000" } ?: "#B3000000"
            val backgroundColor = background.removePrefix("#").toLongOrNull(16)?.let { if (background.length == 7) it or 0xFF000000 else it } ?: 0xB3000000
            Text("I didn’t hear you come in.", Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp * scale)
                .background(if (prefs.subtitleBackground == "Box") Color(backgroundColor) else Color.Transparent).padding(horizontal = 10.dp * scale),
                color = Color(prefs.subtitleColor), fontSize = (font * scale).sp, lineHeight = ((font + 8 * ratio) * scale).sp, fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = if (prefs.subtitleBackground == "Shadow") Shadow(Color.Black, Offset(2f, 2f), 4f) else null))
        }
    }
}
