package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.selectBrowsingProvider(plugin: String) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val current = mutable.value.browsingConfiguration ?: throw LocalFailure("Refresh add-on settings first.")
    if (local != null) {
        if (current.profile.browsing_plugin != plugin) throw LocalFailure("This metadata provider is unavailable on this device.")
    } else requireNotNull(connection).selectBrowsingProvider(profile, current, plugin)
    loadAddons()
    updateAddonHome()
    loadSourceIntegrations()
    mutable.value = mutable.value.copy(browseControls = null, browseCards = emptyList(), results = emptyList())
}
