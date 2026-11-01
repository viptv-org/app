package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.subtitleMode
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.TextButton

@Composable
internal fun PhoneSubtitlePreferences(value: ProfilePreferences, busy: Boolean, update: ((ProfilePreferences) -> ProfilePreferences) -> Unit,
    dialog: (PhonePreferenceField) -> Unit) {
    SubtitleStylePreview(value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Size", color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        PhonePreferenceSegments(listOf("0.75" to "S", "1.0" to "M", "1.25" to "L", "1.5" to "XL"),
            value.subtitle_font_scale.toString(), !busy) { scale -> update { it.copy(subtitle_font_scale = scale.toDouble()) } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Background", Modifier.weight(1f), color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton({ dialog(PhonePreferenceField.BackgroundColour) }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Custom background", fontSize = 12.sp) }
        }
        PhonePreferenceSegments(listOf("none" to "None", "box" to "Box"),
            if (value.subtitle_background_color.equals("#00000000", true)) "none" else "box", !busy) { selected ->
            update { current -> current.copy(subtitle_background_color = if (selected == "none") "#00000000" else
                current.subtitle_background_color.takeUnless { it.equals("#00000000", true) } ?: "#B3000000") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Text colour", Modifier.weight(1f), color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            TextButton({ dialog(PhonePreferenceField.TextColour) }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("Custom colour", fontSize = 12.sp) }
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("White" to "#FFFFFF", "Yellow" to "#FFE16B", "Blue" to "#7AE0EC", "Green" to "#B5EF98").forEach { (label, hex) ->
                val selected = value.subtitle_text_color.equals(hex, true)
                Box(Modifier.size(48.dp).semantics { contentDescription = "Text colour $label" }
                    .border(if (selected) 2.dp else 0.dp, if (selected) Palette.text else Color.Transparent, CircleShape)
                    .pointerSelectable(selected, enabled = !busy, role = Role.RadioButton, onClick = { update { it.copy(subtitle_text_color = hex) } }, shape = CircleShape)
                    .padding(4.dp).clip(CircleShape).background(phonePreferenceColor(hex)))
            }
        }
    }
    PhonePreferenceGroup {
        PhonePreferenceRow("Audio language", phonePreferenceLanguage(value.preferred_audio_language, true), !busy) { dialog(PhonePreferenceField.AudioLanguage) }
        PhonePreferenceDivider()
        PhonePreferenceRow("Subtitles", subtitleModeLabel(value.subtitleMode, value.preferred_audio_language), !busy) { dialog(PhonePreferenceField.SubtitleMode) }
        PhonePreferenceDivider()
        PhonePreferenceRow("Subtitle language", phonePreferenceLanguage(value.preferred_subtitle_language, false), !busy) { dialog(PhonePreferenceField.SubtitleLanguage) }
    }
}
