package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadAddons(operation: AddonOperationOwner? = null) {
    val owner = operation ?: addonOperationOwner()
    owner.requireCurrent(this)
    val profile = owner.profile ?: throw LocalFailure("Choose a profile first.")
    val result = try {
        val device = owner.device
        if (device != null) {
            val addons = device.addons()
            owner.requireCurrent(this)
            val enabled = device.enabledAddons(profile)
            owner.requireCurrent(this)
            val browsing = device.browsingConfiguration(profile)
            RemoteAddons(addons, enabled, browsing)
        } else requireNotNull(owner.remote).addons(profile)
    } catch (error: Exception) { owner.requireCurrent(this); throw error }
    owner.requireCurrent(this)
    publishAddons(result)
}

internal fun ApplicationController.publishAddons(result: RemoteAddons, keepBrowse: Boolean = false) {
    updateState { state -> state.copy(addons = result.addons, enabledAddonIds = result.enabled, browsingConfiguration = result.browsing,
        browseControls = state.browseControls.takeIf { keepBrowse }, browseCards = if (keepBrowse) state.browseCards else emptyList(),
        browseNext = state.browseNext.takeIf { keepBrowse }) }
}

fun ApplicationController.refreshAddons() = run {
    val owner = beginAddonOperation()
    loadAddons(owner); loadSourceIntegrations(owner); loadPreferences(owner)
}

fun ApplicationController.refreshAddonConfiguration() = run {
    val owner = beginAddonOperation()
    loadAddons(owner); loadSourceIntegrations(owner)
}

internal suspend fun ApplicationController.updateAddonHome(operation: AddonOperationOwner? = null) {
    val owner = operation ?: addonOperationOwner()
    owner.requireCurrent(this)
    val profile = owner.profile ?: throw LocalFailure("Choose a profile first.")
    val home = try {
        owner.device?.refreshHome(profile) ?: requireNotNull(owner.remote).bootstrap().home
    } catch (error: Exception) { owner.requireCurrent(this); throw error }
    owner.requireCurrent(this)
    updateState { next -> next.copy(home = home) }
}

fun ApplicationController.installAddon(url: String) = installAddon(url) {}

fun ApplicationController.installAddon(url: String, completion: (Boolean) -> Unit) {
    if (mutable.value.busy) { completion(false); return }
    val device = local
    val remote = connection
    val profile = mutable.value.selectedProfile
    val expectedGeneration = generation + 1
    run {
        markAddonOperation()
        val owner = AddonOperationOwner(expectedGeneration, device, remote, profile, currentCoroutineContext()[Job])
        try {
            val selected = profile ?: throw LocalFailure("Choose a profile first.")
            if (device != null) device.commitAddonInstallation(selected, url)
            else requireNotNull(remote).installAddon(selected, url)
            owner.requireCurrent(this)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            owner.requireCurrent(this)
            completion(false)
            throw error
        }
        completion(true)
        owner.requireCurrent(this)
        loadAddons(owner)
        updateAddonHome(owner)
        loadSourceIntegrations(owner)
        reloadLiveIfVisible()
    }
}

fun ApplicationController.toggleAddon(id: String, enabled: Boolean) = run {
    val owner = beginAddonOperation()
    val profile = requireNotNull(owner.profile)
    if (owner.device != null) owner.device.setAddonEnabled(profile, id, enabled)
    else requireNotNull(owner.remote).setAddonEnabled(profile, id, enabled)
    owner.requireCurrent(this)
    loadAddons(owner)
    updateAddonHome(owner)
    loadSourceIntegrations(owner)
    reloadLiveIfVisible()
}

fun ApplicationController.removeAddon(id: String) = run {
    val owner = beginAddonOperation()
    if (owner.device != null) owner.device.removeAddon(id) else requireNotNull(owner.remote).removeAddon(id)
    owner.requireCurrent(this)
    loadAddons(owner)
    updateAddonHome(owner)
    loadSourceIntegrations(owner)
    reloadLiveIfVisible()
}
