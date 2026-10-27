package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.viptv.app.connection.autoQualityLabel
import org.viptv.app.connection.deviceMaxVideoHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun TvPlaybackSettings(name: String?, prefs: TvSettingsPreferences?, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, change: (TvSettingsPreferences) -> Unit, choose: (String, List<String>, (String) -> Unit) -> Unit,
    unavailable: () -> Unit, actualPreferencesOnly: Boolean = false, devicePlayback: DevicePlaybackState? = null) {
    // Only rows the server stores are shown on a real connection; the placeholder
    // toggles stay in UI previews. Focus starts on the first visible row.
    val rows = tvPlaybackRows(actualPreferencesOnly, deviceRows = devicePlayback != null)
    val deviceHeight = remember { deviceMaxVideoHeight() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Playback", scale, if (prefs != null) "These apply to ${name ?: "this profile"}’s profile." else "Playback preferences are not available on this connection yet.")
        rows.forEachIndexed { index, row ->
            val requester = if (index == 0) initial else null
            when (row) {
                "Next episode button" -> TvSettingsRow(row, "Shows during the credits", scale, checked = prefs?.nextEpisode,
                    requester = requester, left = sidebar) { if (prefs != null) change(prefs.copy(nextEpisode = !prefs.nextEpisode)) else unavailable() }
                autoplayNextRow -> devicePlayback?.let { device ->
                    TvSettingsRow(row, autoplayNextDescription, scale, checked = device.value.autoplayNext, requester = requester, left = sidebar) {
                        device.setAutoplayNext(!device.value.autoplayNext) }
                }
                "Skip intros and recaps" -> TvSettingsRow(row, "Skips them when the show marks where they are", scale, checked = prefs?.skipIntros,
                    requester = requester, left = sidebar) { if (prefs != null) change(prefs.copy(skipIntros = !prefs.skipIntros)) else unavailable() }
                "Default source" -> TvSettingsRow(row, scale = scale, value = prefs?.source ?: "Unavailable",
                    requester = requester, left = sidebar) {
                    if (prefs != null) choose(row, listOf("Best match", "Always ask")) { change(prefs.copy(source = it)) } else unavailable() }
                "Preferred quality" -> TvSettingsRow(row, scale = scale,
                    value = prefs?.let { tvQualityLabel(it.quality, deviceHeight) } ?: "Unavailable", requester = requester, left = sidebar) {
                    if (prefs != null) choose(row, listOf("Auto", "4K", "1080p", "720p")) { change(prefs.copy(quality = it)) } else unavailable() }
                "HDR and Dolby Vision" -> TvSettingsRow(row, scale = scale, value = prefs?.hdr ?: "Unavailable",
                    requester = requester, left = sidebar) {
                    if (prefs != null) choose(row, hdrPreferenceChoices.map { it.second }) { change(prefs.copy(hdr = it)) } else unavailable() }
                "Match frame rate" -> TvSettingsRow(row, "Switches the TV to the video’s frame rate for smoother motion", scale, checked = prefs?.frameRate,
                    requester = requester, left = sidebar) { if (prefs != null) change(prefs.copy(frameRate = !prefs.frameRate)) else unavailable() }
            }
        }
    }
}

/** `Auto` names this device's own resolution when known; fixed caps show as chosen. */
internal fun tvQualityLabel(quality: String, deviceHeight: Int?): String =
    if (quality == "Auto" || quality == "Best available") autoQualityLabel(deviceHeight) else quality
