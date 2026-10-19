package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.connection.SourceCredentialField
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.connection.SourceProviderChoice
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
        "Sources" -> TvSettingsSection.Sources;
        "SignOut", "Account" -> TvSettingsSection.Account; else -> TvSettingsSection.Playback }
    val sources = remember { SourceIntegrationSnapshot(listOf(
        SourceIntegrationSchema("viptv_sources", "VIPTV Sources", "Ready", runtime_enabled = true, connection_id = "viptv-sources",
            credential_fields = listOf(SourceCredentialField("tmdb_key", "TMDB key")), provider_choices = List(8) { SourceProviderChoice("p$it", "Provider $it") }),
        SourceIntegrationSchema("xtream", "Xtream live TV", "Needs configuration", runtime_enabled = true,
            credential_fields = listOf(SourceCredentialField("server", "Server"), SourceCredentialField("username", "Username"), SourceCredentialField("password", "Password"))),
        SourceIntegrationSchema("m3u", "M3U live TV", "Needs configuration"),
    )) }
    TvSettingsScreen(variant != "SignOut", addons, enabled, false, null, null, {}, { id, value -> enabled = if (value) enabled + id else enabled - id },
        { go(Screen.Profiles) }, { go(Screen.Connect) }, PreviewData.profile, initialSection = section, prefs = prefs,
        change = { prefs = it }, addonInfo = info, account = TvAccountInfo("[name@email.com]", 3), initialLeave = variant == "SignOut", placeholderQr = true,
        sourceIntegrations = { first, sidebar, _ -> SourceIntegrationSettings(sources, false, null, {}, { _, _ -> }, {}, television = true, profileName = "vynxc", first = first, left = sidebar) })
}
