package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Button
import org.viptv.app.components.SearchableSelect
import org.viptv.app.components.SelectOption
import org.viptv.app.components.TextButton
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette

private val subtitleSizeOptions = listOf(0.75 to "Small", 1.0 to "Medium", 1.25 to "Large", 1.5 to "Extra large")
private val sourceOptions = listOf(SelectOption("best", "Best match"), SelectOption("ask", "Always ask"))
private val hdrOptions = hdrPreferenceChoices.map { (value, label) -> SelectOption(value, label) }
private val textColourOptions = listOf(
    SelectOption("#FFFFFF", "White"), SelectOption("#FFE16B", "Yellow"),
    SelectOption("#7AE0EC", "Blue"), SelectOption("#B5EF98", "Green"))
private val languageOptions = languageChoices.map { (tag, name) -> SelectOption(tag, languageOptionLabel(name, tag)) }

@Composable
internal fun ProfilePreferencesSettings(section: String, snapshot: PreferencesSnapshot?, busy: Boolean, error: String?,
    retry: () -> Unit, save: (ProfilePreferences) -> Unit, devicePlayback: DevicePlaybackState? = null) {
    var draft by remember(snapshot) { mutableStateOf(snapshot?.preferences) }
    val deviceHeight = remember { deviceMaxVideoHeight() }
    org.viptv.app.components.TvFocusScroll(horizontal = false) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(section, style = MaterialTheme.typography.headlineSmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = Palette.danger) }
            val value = draft
            if (value == null || error != null) TextButton(retry, enabled = !busy) { Text(if (value == null && error == null) "Load settings" else "Retry settings") }
            if (section == "Playback" && devicePlayback != null) {
                PhonePreferenceGroup { PhonePreferenceSwitchRow(autoplayNextRow, autoplayNextDescription, devicePlayback.value.autoplayNext, devicePlayback::setAutoplayNext) }
            }
            if (value != null) {
                if (section == "Subtitles & audio") {
                    SubtitleStylePreview(value)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchableSelect("Text size", subtitleSizeOptions.map { (scale, label) -> SelectOption(scale.toString(), label) },
                            listOf(value.subtitle_font_scale.toString()),
                            { ids -> ids.firstOrNull()?.toDoubleOrNull()?.let { draft = draft?.copy(subtitle_font_scale = it) } },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("Background", listOf(SelectOption("none", "None"), SelectOption("box", "Box")),
                            listOf(if (value.subtitle_background_color.equals("#00000000", true)) "none" else "box"),
                            { ids -> draft = draft?.copy(subtitle_background_color = when (ids.firstOrNull()) {
                                "none" -> "#00000000"; else -> value.subtitle_background_color.takeUnless { it.equals("#00000000", true) } ?: "#B3000000" }) },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("Text colour", textColourOptions, listOf(value.subtitle_text_color.uppercase()),
                            { ids -> ids.firstOrNull()?.let { draft = draft?.copy(subtitle_text_color = it) } },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("Audio language", languageOptions, listOfNotNull(value.preferred_audio_language),
                            { ids -> draft = draft?.copy(preferred_audio_language = ids.firstOrNull()) },
                            anyLabel = "Original audio", searchPlaceholder = "Search audio languages")
                        SearchableSelect("Subtitles", subtitleModeChoices(value.preferred_audio_language).map { (mode, label) -> SelectOption(mode, label) },
                            listOf(value.subtitleMode),
                            { ids -> ids.firstOrNull()?.let { mode -> draft = draft?.withSubtitleMode(mode) } },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("Subtitle language", languageOptions, listOfNotNull(value.preferred_subtitle_language),
                            { ids -> draft = draft?.copy(preferred_subtitle_language = ids.firstOrNull()) },
                            anyLabel = "None", searchPlaceholder = "Search subtitle languages")
                    }
                } else {
                    PhonePreferenceGroup {
                        PhonePreferenceSwitchRow(nextEpisodeButtonRow, nextEpisodeButtonDescription, value.next_episode_button) { draft = draft?.copy(next_episode_button = it) }
                        PhonePreferenceDivider()
                        PhonePreferenceSwitchRow(skipIntrosRow, skipIntrosDescription, value.skip_intros) { draft = draft?.copy(skip_intros = it) }
                    }
                    Text("Sources", color = Palette.secondary, style = MaterialTheme.typography.bodySmall)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchableSelect("Default source", sourceOptions, listOf(value.source_selection),
                            { ids -> ids.firstOrNull()?.let { draft = draft?.copy(source_selection = it) } },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("Preferred quality", qualityOptions(value.max_quality_height, deviceHeight),
                            listOf(value.max_quality_height?.toString().orEmpty()),
                            { ids -> draft = draft?.copy(max_quality_height = ids.firstOrNull()?.toIntOrNull()) },
                            anyLabel = null, placeholder = "Choose")
                        SearchableSelect("HDR and Dolby Vision", hdrOptions, listOf(value.hdr_preference),
                            { ids -> ids.firstOrNull()?.let { draft = draft?.copy(hdr_preference = it) } },
                            anyLabel = null, placeholder = "Choose")
                    }
                }
                Button({ save(value) }, enabled = !busy && value != snapshot?.preferences, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
            }
        }
    }
}

private fun qualityOptions(current: Int?, deviceHeight: Int?): List<SelectOption> =
    (listOf<Int?>(null, 720, 1080, 2160) + current).distinct().map { height ->
        SelectOption(height?.toString().orEmpty(), qualityLabel(height, deviceHeight))
    }