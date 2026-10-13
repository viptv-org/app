package org.viptv.experiment

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.state.*

/** Uses a public playlist in its own native database; no VIPTV backend or guide fixtures. */
internal suspend fun ApplicationController.prepareLocalLive(path: String, playlist: String) {
    useLocal(path)
    state.first { !it.busy && it.profiles.isNotEmpty() }
    selectProfile(state.value.profiles.first { !it.locked }.id)
    state.first { !it.busy && it.selectedProfile != null }
    refreshSourceIntegrations()
    state.first { !it.busy }
    check(state.value.sourceIntegrationError == null)
    if (state.value.sourceIntegrations.connections.none { it.plugin_id == "m3u" }) {
        configureSourceIntegration("m3u", buildJsonObject { put("playlist_url", playlist) })
        state.first { !it.busy }
        check(state.value.error == null)
    }
    navigate(Screen.Live)
}
