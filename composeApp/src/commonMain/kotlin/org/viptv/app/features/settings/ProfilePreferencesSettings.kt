package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
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
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ask" to "Always ask", "best" to "Best match").forEach { (value, label) ->
                    FilterChip(selection == value, { selection = value }, label = { Text(label) }, enabled = !busy)
                }
            }
            Text("Preferred quality")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, 720, 1080, 2160).forEach { height ->
                    FilterChip(quality == height, { quality = height }, label = { Text(height?.let { if (it == 2160) "4K" else "${it}p" } ?: "Best available") }, enabled = !busy)
                }
            }
            Button({ save(ProfilePreferences(audio.trim().takeIf(String::isNotEmpty), subtitle.trim().takeIf(String::isNotEmpty), enabled, selection, quality)) }, enabled = !busy) { Text("Save preferences") }
        }
    }
}
