package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

@Composable
internal fun ProfilePreferencesSettings(snapshot: PreferencesSnapshot?, busy: Boolean, error: String?,
    retry: () -> Unit, save: (ProfilePreferences) -> Unit) {
    val preferences = snapshot?.preferences
    var audio by remember(snapshot) { mutableStateOf(preferences?.preferred_audio_language.orEmpty()) }
    var subtitle by remember(snapshot) { mutableStateOf(preferences?.preferred_subtitle_language.orEmpty()) }
    var enabled by remember(snapshot) { mutableStateOf(preferences?.subtitles_enabled ?: true) }
    var selection by remember(snapshot) { mutableStateOf(preferences?.source_selection ?: "ask") }
    var quality by remember(snapshot) { mutableStateOf(preferences?.max_quality_height) }
    var subtitleScale by remember(snapshot) { mutableStateOf(preferences?.subtitle_font_scale ?: 1.0) }
    var subtitleColor by remember(snapshot) { mutableStateOf(preferences?.subtitle_text_color ?: "#FFFFFF") }
    var subtitleBackground by remember(snapshot) { mutableStateOf(preferences?.subtitle_background_color ?: "#00000000") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Playback preferences", style = MaterialTheme.typography.headlineSmall)
        Text("These preferences apply to the current profile.", color = Palette.secondary)
        error?.let { Text(it, color = Palette.danger) }
        TextButton(retry, enabled = !busy) { Text("Refresh") }
        if (preferences != null) {
            OutlinedTextField(audio, { audio = it }, label = { Text("Preferred audio language") },
                supportingText = { Text("Language tag, for example en or pt-BR. Leave empty for original audio.") }, enabled = !busy, singleLine = true)
            OutlinedTextField(subtitle, { subtitle = it }, label = { Text("Preferred subtitle language") },
                supportingText = { Text("Leave empty to use the media’s default subtitle track.") }, enabled = !busy, singleLine = true)
            Row { Switch(enabled, { enabled = it }, enabled = !busy); Text("Start with subtitles", Modifier.padding(12.dp)) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ask" to "Always ask", "best" to "Best match").forEach { (value, label) ->
                    FilterChip(selection == value, { selection = value }, label = { Text(label) }, enabled = !busy)
                }
            }
            Text("Preferred quality")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, 720, 1080, 2160).forEach { height ->
                    FilterChip(quality == height, { quality = height }, label = { Text(height?.let { if (it == 2160) "4K" else "${it}p" } ?: "Best available") }, enabled = !busy)
                }
            }
            Text("Subtitle size")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(.75 to "Small", 1.0 to "Medium", 1.25 to "Large", 1.5 to "Extra large").forEach { (value, label) ->
                    FilterChip(subtitleScale == value, { subtitleScale = value }, label = { Text(label) }, enabled = !busy)
                }
            }
            OutlinedTextField(subtitleColor, { subtitleColor = it }, label = { Text("Subtitle text colour") },
                supportingText = { Text("Hex colour, for example #FFFFFF.") }, enabled = !busy, singleLine = true)
            OutlinedTextField(subtitleBackground, { subtitleBackground = it }, label = { Text("Subtitle background colour") },
                supportingText = { Text("Hex colour with optional alpha; #00000000 is transparent.") }, enabled = !busy, singleLine = true)
            Button({ save(preferences.copy(preferred_audio_language = audio.trim().takeIf(String::isNotEmpty), preferred_subtitle_language = subtitle.trim().takeIf(String::isNotEmpty),
                subtitles_enabled = enabled, source_selection = selection, max_quality_height = quality,
                subtitle_font_scale = subtitleScale, subtitle_text_color = subtitleColor.trim(), subtitle_background_color = subtitleBackground.trim())) }, enabled = !busy) { Text("Save preferences") }
        }
    }
}
