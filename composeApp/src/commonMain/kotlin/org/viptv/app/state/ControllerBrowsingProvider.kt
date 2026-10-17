package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.selectBrowsingProvider(plugin: String) = run {
    val owner = addonOperationOwner()
    val profile = requireNotNull(owner.profile)
    val current = mutable.value.browsingConfiguration ?: throw LocalFailure("Refresh add-on settings first.")
    if (owner.device != null) {
        if (current.profile.browsing_plugin != plugin) throw LocalFailure("This metadata provider is unavailable on this device.")
    } else requireNotNull(owner.remote).selectBrowsingProvider(profile, current, plugin)
    loadAddons(owner)
    updateAddonHome(owner)
    loadSourceIntegrations(owner)
    owner.requireCurrent(this)
    mutable.value = mutable.value.copy(browseControls = null, browseCards = emptyList(), results = emptyList())
}
