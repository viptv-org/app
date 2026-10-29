package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode
import org.viptv.app.connection.withSubtitleMode
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.TextButton

internal enum class PhonePreferenceField { Source, Quality, Hdr, SubtitleMode, AudioLanguage, SubtitleLanguage, TextColour, BackgroundColour }

internal fun phonePreferenceTag(value: String): Boolean = value.isEmpty() || value.length <= 32 &&
    value.split('-').let { parts -> parts.first().length in 2..3 && parts.withIndex().all { (i, part) ->
        part.isNotEmpty() && part.length <= 8 && part.all { it in 'A'..'Z' || it in 'a'..'z' || i > 0 && it in '0'..'9' }
    } }

@Composable
internal fun PhonePreferenceDialog(field: PhonePreferenceField, value: ProfilePreferences, busy: Boolean,
    update: (ProfilePreferences) -> Unit, close: () -> Unit, keyboardInsets: WindowInsets? = null) {
    val title = when (field) {
        PhonePreferenceField.Source -> "Default source"; PhonePreferenceField.Quality -> "Preferred quality"
        PhonePreferenceField.Hdr -> "HDR and Dolby Vision"; PhonePreferenceField.SubtitleMode -> "Subtitles"
        PhonePreferenceField.AudioLanguage -> "Audio language"; PhonePreferenceField.SubtitleLanguage -> "Subtitle language"
        PhonePreferenceField.TextColour -> "Text colour"; PhonePreferenceField.BackgroundColour -> "Background colour"
    }
    val current = when (field) {
        PhonePreferenceField.Source -> value.source_selection; PhonePreferenceField.Quality -> value.max_quality_height?.toString().orEmpty()
        PhonePreferenceField.Hdr -> value.hdr_preference; PhonePreferenceField.SubtitleMode -> value.subtitleMode
        PhonePreferenceField.AudioLanguage -> value.preferred_audio_language.orEmpty(); PhonePreferenceField.SubtitleLanguage -> value.preferred_subtitle_language.orEmpty()
        PhonePreferenceField.TextColour -> value.subtitle_text_color; PhonePreferenceField.BackgroundColour -> value.subtitle_background_color
    }
    val language = field in listOf(PhonePreferenceField.AudioLanguage, PhonePreferenceField.SubtitleLanguage)
    val custom = language || field in listOf(PhonePreferenceField.TextColour, PhonePreferenceField.BackgroundColour)
    var entry by remember(field) { mutableStateOf(current) }
    fun apply(selected: String) {
        update(when (field) {
            PhonePreferenceField.Source -> value.copy(source_selection = selected)
            PhonePreferenceField.Quality -> value.copy(max_quality_height = selected.toIntOrNull())
            PhonePreferenceField.Hdr -> value.copy(hdr_preference = selected)
            PhonePreferenceField.SubtitleMode -> value.withSubtitleMode(selected)
            PhonePreferenceField.AudioLanguage -> value.copy(preferred_audio_language = selected.takeIf(String::isNotEmpty))
            PhonePreferenceField.SubtitleLanguage -> value.copy(preferred_subtitle_language = selected.takeIf(String::isNotEmpty))
            PhonePreferenceField.TextColour -> value.copy(subtitle_text_color = selected.uppercase())
            PhonePreferenceField.BackgroundColour -> value.copy(subtitle_background_color = selected.uppercase())
        }); close()
    }
    val choices = when (field) {
        PhonePreferenceField.Source -> listOf("best" to "Best match", "ask" to "Always ask")
        PhonePreferenceField.Quality -> (listOf<Int?>(null, 720, 1080, 2160) + value.max_quality_height).distinct().map {
            it?.toString().orEmpty() to (it?.let { height -> if (height == 2160) "4K" else "${height}p" } ?: "Auto") }
        PhonePreferenceField.Hdr -> hdrPreferenceChoices
        PhonePreferenceField.SubtitleMode -> subtitleModeChoices(value.preferred_audio_language)
        PhonePreferenceField.AudioLanguage, PhonePreferenceField.SubtitleLanguage -> listOf("", "en", "es", "pt", "fr", "de").map {
            it to phonePreferenceLanguage(it, field == PhonePreferenceField.AudioLanguage) }
        else -> emptyList()
    }
    val valid = if (language) phonePreferenceTag(entry) else entry.startsWith('#') &&
        (entry.length == 7 || field == PhonePreferenceField.BackgroundColour && entry.length == 9) && entry.drop(1).all { it.isDigit() && it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
    // Material's text slot shrinks and scrolls while its title and actions stay fixed, so apply IME padding inside this dialog's layout.
    AlertDialog(onDismissRequest = { if (!busy) close() },
        modifier = Modifier.composed { windowInsetsPadding(keyboardInsets ?: WindowInsets.ime) }, title = { Text(title) },
        text = { org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, label) -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).pointerSelectable(current.equals(key, true),
                enabled = !busy, role = Role.RadioButton, onClick = { apply(key) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(current.equals(key, true), onClick = null, enabled = !busy); Spacer(Modifier.width(8.dp)); Text(label)
            } }
            if (custom) OutlinedTextField(entry, { entry = it.take(if (language) 32 else 9) }, singleLine = true, enabled = !busy,
                label = { Text(if (language) "Language tag" else title) }, supportingText = { Text(if (language) "For example en or pt-BR. Empty uses the default."
                    else if (field == PhonePreferenceField.BackgroundColour) "Use #RRGGBB or #AARRGGBB, including transparency." else "Use #RRGGBB.") })
        } } }, confirmButton = {
            if (custom) TextButton({ apply(entry) }, enabled = !busy && valid) { Text("Use value") }
        }, dismissButton = { TextButton(close, enabled = !busy) { Text("Cancel") } })
}
