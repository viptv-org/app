package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun TvPlaybackSettings(name: String?, prefs: TvSettingsPreferences?, scale: Float, initial: FocusRequester,
    sidebar: FocusRequester, change: (TvSettingsPreferences) -> Unit, choose: (String, List<String>, (String) -> Unit) -> Unit,
    unavailable: () -> Unit, actualPreferencesOnly: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Playback", scale, if (prefs != null) "These apply to ${name ?: "this profile"}’s profile." else "Playback preferences are not available on this connection yet.")
        TvSettingsRow("Next episode button", "Shows during the credits. Nothing ever plays on its own.", scale, checked = if (actualPreferencesOnly) null else prefs?.nextEpisode,
            requester = initial, left = sidebar) { if (!actualPreferencesOnly && prefs != null) change(prefs.copy(nextEpisode = !prefs.nextEpisode)) else unavailable() }
        TvSettingsRow("Skip intros and recaps", "Skips them when the show marks where they are", scale, checked = if (actualPreferencesOnly) null else prefs?.skipIntros, left = sidebar) {
            if (!actualPreferencesOnly && prefs != null) change(prefs.copy(skipIntros = !prefs.skipIntros)) else unavailable() }
        TvSettingsRow("Default source", "How Play chooses between sources", scale, value = prefs?.source ?: "Unavailable", left = sidebar) {
            if (prefs != null) choose("Default source", listOf("Best match", "Always ask")) { change(prefs.copy(source = it)) } else unavailable() }
        TvSettingsRow("Preferred quality", "Best match aims for this, then falls back", scale, value = prefs?.quality ?: "Unavailable", left = sidebar) {
            if (prefs != null) choose("Preferred quality", listOf("Best available", "4K", "1080p", "720p")) { change(prefs.copy(quality = it)) } else unavailable() }
        TvSettingsRow("Match frame rate", "Switches the TV to the video’s frame rate for smoother motion", scale, checked = if (actualPreferencesOnly) null else prefs?.frameRate, left = sidebar) {
            if (!actualPreferencesOnly && prefs != null) change(prefs.copy(frameRate = !prefs.frameRate)) else unavailable() }
        TvSettingsRow("Start with subtitles", scale = scale, value = prefs?.startSubtitles ?: "Unavailable", left = sidebar) {
            if (prefs != null) choose("Start with subtitles", listOf("Off", "On")) { change(prefs.copy(startSubtitles = it)) } else unavailable() }
    }
}
