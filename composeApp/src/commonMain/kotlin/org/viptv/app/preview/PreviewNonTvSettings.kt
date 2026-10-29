package org.viptv.app.preview

import androidx.compose.runtime.*
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.state.Screen
import org.viptv.engine.wire.*

@Composable
internal fun PreviewNonTvSettings(variant: String, go: (Screen) -> Unit) {
    var addons by remember { mutableStateOf(listOf(
        IntegrationConnection("addon:1", "stremio", "preview", null, "[Add-on A]", true, 1, listOf(Capability.METADATA, Capability.SOURCES)),
        IntegrationConnection("addon:2", "stremio", "preview", null, "[Add-on B]", true, 1, listOf(Capability.SOURCES)),
        IntegrationConnection("addon:3", "stremio", "preview", null, "[Add-on C]", true, 1, listOf(Capability.LIVETV)))) }
    var enabled by remember { mutableStateOf(setOf("addon:1", "addon:2")) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsScreen(variant !in listOf("RemoteAddons", "SignOut"), addons, enabled, false, error, null,
        { error = "This isolated preview does not install add-ons. Use a live connection." },
        { id, value -> enabled = if (value) enabled + id else enabled - id },
        { id -> addons = addons.filterNot { it.id == id }; enabled = enabled - id },
        { go(Screen.Profiles) }, { go(Screen.Connect) }, PreviewData.profile, { go(Screen.Home) },
        initialSection = if (variant in listOf("Addons", "RemoteAddons")) "Add-ons" else if (variant == "SignOut") "Account" else null,
        initialLeave = variant == "SignOut")
}
