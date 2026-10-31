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
internal fun TvPlaybackSettings(name: String?, prefs: TvSettingsPreferences, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, change: (TvSettingsPreferences) -> Unit, choose: (String, List<String>, (String) -> Unit) -> Unit,
    actualPreferencesOnly: Boolean = false, devicePlayback: DevicePlaybackState? = null) {
    val rows = tvPlaybackRows(actualPreferencesOnly, deviceRows = devicePlayback != null)
    val deviceHeight = remember { deviceMaxVideoHeight() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Playback", scale, "These apply to ${name ?: "this profile"}’s profile.")
        rows.forEachIndexed { index, row ->
            val requester = if (index == 0) initial else null
            when (row) {
                nextEpisodeButtonRow -> TvSettingsRow(nextEpisodeButtonRow, nextEpisodeButtonDescription, scale, checked = prefs.nextEpisode,
                    requester = requester, left = sidebar) { change(prefs.copy(nextEpisode = !prefs.nextEpisode)) }
                autoplayNextRow -> devicePlayback?.let { device ->
                    TvSettingsRow(row, autoplayNextDescription, scale, checked = device.value.autoplayNext, requester = requester, left = sidebar) {
                        device.setAutoplayNext(!device.value.autoplayNext) }
                }
                skipIntrosRow -> TvSettingsRow(skipIntrosRow, skipIntrosDescription, scale, checked = prefs.skipIntros,
                    requester = requester, left = sidebar) { change(prefs.copy(skipIntros = !prefs.skipIntros)) }
                "Default source" -> TvSettingsRow(row, scale = scale, value = prefs.source,
                    requester = requester, left = sidebar) {
                    choose(row, listOf("Best match", "Always ask")) { change(prefs.copy(source = it)) } }
                "Preferred quality" -> TvSettingsRow(row, scale = scale,
                    value = tvQualityLabel(prefs.quality, deviceHeight), requester = requester, left = sidebar) {
                    choose(row, listOf("Auto", "4K", "1080p", "720p")) { change(prefs.copy(quality = it)) } }
                "HDR and Dolby Vision" -> TvSettingsRow(row, scale = scale, value = prefs.hdr,
                    requester = requester, left = sidebar) {
                    choose(row, hdrPreferenceChoices.map { it.second }) { change(prefs.copy(hdr = it)) } }
                "Match frame rate" -> TvSettingsRow(row, "Switches the TV to the video’s frame rate for smoother motion", scale, checked = prefs.frameRate,
                    requester = requester, left = sidebar) { change(prefs.copy(frameRate = !prefs.frameRate)) }
            }
        }
    }
}

internal fun tvQualityLabel(quality: String, deviceHeight: Int?): String =
    if (quality == "Auto" || quality == "Best available") autoQualityLabel(deviceHeight) else quality
