package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
internal fun PhoneProfilePreferences(section: String, snapshot: PreferencesSnapshot?, busy: Boolean, error: String?,
    retry: () -> Unit, save: (ProfilePreferences) -> Unit, devicePlayback: DevicePlaybackState? = null) {
    var draft by remember(snapshot) { mutableStateOf(snapshot?.preferences) }
    var dialog by remember(section) { mutableStateOf<PhonePreferenceField?>(null) }
    val deviceHeight = remember { deviceMaxVideoHeight() }
    org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(section, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = Palette.danger) }
        val value = draft
        if (value == null || error != null) TextButton(retry, enabled = !busy) { Text(if (value == null && error == null) "Load settings" else "Retry settings") }
        // Stored on this device, so it shows and applies at once even while the profile's preferences can't load.
        if (section == "Playback" && devicePlayback != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Next episode", Modifier.padding(horizontal = 4.dp), color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            PhonePreferenceGroup {
                PhonePreferenceSwitchRow(autoplayNextRow, autoplayNextDescription, devicePlayback.value.autoplayNext, devicePlayback::setAutoplayNext)
            }
        }
        if (value != null) {
            if (section == "Subtitles & audio") PhoneSubtitlePreferences(value, busy, { edit -> draft = draft?.let(edit) }, { dialog = it })
            else {
                Text("Playback resumes from your saved position.", color = Palette.secondary, fontSize = 13.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sources", Modifier.padding(horizontal = 4.dp), color = Palette.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    PhonePreferenceGroup {
                        PhonePreferenceRow("Default source", if (value.source_selection == "best") "Best match" else "Always ask", !busy) { dialog = PhonePreferenceField.Source }
                        PhonePreferenceDivider()
                        PhonePreferenceRow("Preferred quality", qualityLabel(value.max_quality_height, deviceHeight), !busy) { dialog = PhonePreferenceField.Quality }
                        PhonePreferenceDivider()
                        PhonePreferenceRow("HDR and Dolby Vision", hdrPreferenceLabel(value.hdr_preference), !busy) { dialog = PhonePreferenceField.Hdr }
                    }
                }
            }
            Button({ save(value) }, enabled = !busy && value != snapshot?.preferences, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
        }
    } }
    dialog?.let { field -> draft?.let { value -> PhonePreferenceDialog(field, value, busy, { draft = it }, { dialog = null }) } }
}
