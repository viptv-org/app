package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

@Composable
internal fun PreviewTvSettings(variant: String, go: (Screen) -> Unit) {
    var prefs by remember { mutableStateOf(TvSettingsPreferences()) }
    var enabled by remember { mutableStateOf(setOf("addon-a", "addon-b")) }
    val addons = remember { listOf(
        IntegrationConnection("addon-a", "stremio", "preview", null, "[Add-on A]", true, 1, listOf(Capability.METADATA, Capability.SOURCES)),
        IntegrationConnection("addon-b", "stremio", "preview", null, "[Add-on B]", true, 1, listOf(Capability.SOURCES)),
        IntegrationConnection("addon-c", "stremio", "preview", null, "[Add-on C]", true, 1, listOf(Capability.LIVETV))) }
    val info = remember { mapOf("addon-a" to TvAddonInfo("Movies and series · [1,240] titles", 0xFF3D6C88, "A"),
        "addon-b" to TvAddonInfo("Movies · [860] titles", 0xFF8151A0, "B"),
        "addon-c" to TvAddonInfo("Live channels · [312] channels", 0xFF4F9273, "C")) }
    val section = when (variant) { "Subs" -> TvSettingsSection.Subtitles; "Addons" -> TvSettingsSection.Addons;
        "SignOut", "Account" -> TvSettingsSection.Account; else -> TvSettingsSection.Playback }
    TvSettingsScreen(variant != "SignOut", addons, enabled, false, null, null, {}, { id, value -> enabled = if (value) enabled + id else enabled - id },
        { go(Screen.Profiles) }, { go(Screen.Connect) }, PreviewData.profile, initialSection = section, prefs = prefs,
        change = { prefs = it }, addonInfo = info, account = TvAccountInfo("[name@email.com]", 3), initialLeave = variant == "SignOut", placeholderQr = true)
}
