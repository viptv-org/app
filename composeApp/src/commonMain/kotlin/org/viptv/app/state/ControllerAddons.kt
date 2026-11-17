package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
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

private fun ApplicationController.leaveAnimeIfUnavailable() {
    if (mutable.value.screen == Screen.Anime && !mutable.value.animeAvailable) navigate(Screen.Home, record = false)
}

internal fun ApplicationController.publishAddons(result: RemoteAddons, keepBrowse: Boolean = false) {
    updateState { state -> state.copy(addons = result.addons, addonsLoaded = true, addonLogos = result.logos, enabledAddonIds = result.enabled, browsingConfiguration = result.browsing,
        configurableAddons = result.configurable,
        browseControls = state.browseControls.takeIf { keepBrowse }, browseCards = if (keepBrowse) state.browseCards else emptyList(),
        browseNext = state.browseNext.takeIf { keepBrowse }) }
    leaveAnimeIfUnavailable()
}

// Phone, tablet, desktop and web open the add-on's configure page in the browser; TV asks for the link to show it as a QR code.
fun ApplicationController.configureAddon(addon: String, openInBrowser: Boolean = true) {
    val state = mutable.value
    val name = state.addons.firstOrNull { it.id == addon }?.name ?: addon
    updateState { it.copy(addonConfigure = AddonConfigureRequest(addon, name)) }
    scope.launch {
        val link = try {
            requireNotNull(connection) { "This add-on is not on a server connection." }.addonConfigureLink(addon)
        } catch (error: CancellationException) { throw error } catch (error: Exception) {
            updateState { current -> current.copy(addonConfigure = current.addonConfigure?.copy(failure = error.message ?: ADDON_CONFIGURE_FAILURE)) }
            return@launch
        }
        if (mutable.value.addonConfigure?.addon == addon) updateState { current ->
            current.copy(addonConfigure = current.addonConfigure?.copy(link = link.url, expiresInSeconds = link.expiresInSeconds))
        }
        if (openInBrowser) browserOpen(link.url)
    }
}

fun ApplicationController.dismissAddonConfigure() { updateState { it.copy(addonConfigure = null) } }

const val ADDON_CONFIGURE_FAILURE = "Couldn’t get the configure link."

internal fun ApplicationController.startBrowsingConfiguration(profile: String) {
    val device = local
    val remote = connection
    scope.launch {
        val result = try {
            device?.browsingConfiguration(profile) ?: remote?.browsingConfiguration(profile)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (mutable.value.selectedProfile != profile || connection !== remote || local !== device) return@launch
        updateState { it.copy(browsingConfiguration = result) }
        leaveAnimeIfUnavailable()
    }
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

internal const val ADDON_INSTALL_TARGET = "Install add-on"

fun ApplicationController.installAddon(url: String, completion: (Boolean) -> Unit) {
    val target = MutationTarget(MutationKind.Addon, ADDON_INSTALL_TARGET)
    // Another operation owns the controller job; a submission must not cancel or replace it.
    if (mutable.value.busy || mutable.value.mutations.isPending(target)) { completion(false); return }
    val device = local
    val remote = connection
    val profile = mutable.value.selectedProfile
    var reported = false
    fun report(success: Boolean) { if (!reported) { reported = true; completion(success) } else Unit }
    mutate(target,
        revert = { report(false); it },
        message = { error -> report(false); failureMessage(error) ?: COULD_NOT_SAVE },
        ownsControllerJob = true) {
        markAddonOperation()
        val owner = AddonOperationOwner(generation, device, remote, profile, currentCoroutineContext()[Job])
        try {
            val selected = profile ?: throw LocalFailure("Choose a profile first.")
            if (device != null) device.commitAddonInstallation(selected, url)
            else requireNotNull(remote).installAddon(selected, url)
            owner.requireCurrent(this)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            // A replaced or cancelled operation must not publish a late failure: requireCurrent throws
            // CancellationException instead, which mutate settles silently.
            owner.requireCurrent(this)
            throw error
        }
        report(true)
        loadAddons(owner)
        updateAddonHome(owner)
        loadSourceIntegrations(owner)
        reloadLiveIfVisible()
    }
}

fun ApplicationController.toggleAddon(id: String, enabled: Boolean) {
    val before = mutable.value
    mutate(MutationTarget(MutationKind.Addon, id),
        optimistic = { state -> state.copy(enabledAddonIds = if (enabled) state.enabledAddonIds + id else state.enabledAddonIds - id) },
        revert = { it.copy(enabledAddonIds = before.enabledAddonIds) },
        ownsControllerJob = true) {
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
}

fun ApplicationController.removeAddon(id: String) {
    val before = mutable.value
    mutate(MutationTarget(MutationKind.Addon, id),
        optimistic = { state -> state.copy(addons = state.addons.filterNot { it.id == id },
            enabledAddonIds = state.enabledAddonIds - id) },
        revert = { it.copy(addons = before.addons, enabledAddonIds = before.enabledAddonIds) },
        ownsControllerJob = true) {
        val owner = beginAddonOperation()
        if (owner.device != null) owner.device.removeAddon(id) else requireNotNull(owner.remote).removeAddon(id)
        owner.requireCurrent(this)
        loadAddons(owner)
        updateAddonHome(owner)
        loadSourceIntegrations(owner)
        reloadLiveIfVisible()
    }
}
