package org.viptv.app.state

internal suspend fun ApplicationController.profileConfigurationChanged(owner: AddonOperationOwner? = null) {
    mutable.value.selectedProfile?.let { startBrowsingConfiguration(it) }
    loadSourceIntegrations(owner)
    requestQuiet(QuietWork.Refresh)
    reloadLiveIfVisible()
}

internal suspend fun ApplicationController.reloadLiveIfVisible() {
    if (mutable.value.screen == Screen.Live) loadLive(mutable.value.liveQuery.copy(offset = 0))
}