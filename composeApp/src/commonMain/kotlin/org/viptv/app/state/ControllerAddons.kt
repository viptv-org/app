package org.viptv.app.state

import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadAddons() {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val device = local
    if (device != null) mutable.value = mutable.value.copy(addons = device.addons(), enabledAddonIds = device.enabledAddons(profile))
    else requireNotNull(connection).addons(profile).let { result ->
        mutable.value = mutable.value.copy(addons = result.addons, enabledAddonIds = result.enabled)
    }
}

fun ApplicationController.refreshAddons() = run { loadAddons() }

internal suspend fun ApplicationController.updateAddonHome() {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val device = local
    val home = if (device != null) device.refreshHome(profile) else requireNotNull(connection).bootstrap().home
    mutable.value = mutable.value.copy(home = home)
}

fun ApplicationController.installAddon(url: String) = run {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).installAddon(profile, url)
    else requireNotNull(connection).installAddon(profile, url)
    loadAddons()
    updateAddonHome()
}

fun ApplicationController.toggleAddon(id: String, enabled: Boolean) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    if (local != null) requireNotNull(local).setAddonEnabled(profile, id, enabled)
    else requireNotNull(connection).setAddonEnabled(profile, id, enabled)
    loadAddons()
    updateAddonHome()
}

fun ApplicationController.removeAddon(id: String) = run {
    if (local != null) requireNotNull(local).removeAddon(id) else requireNotNull(connection).removeAddon(id)
    loadAddons()
    updateAddonHome()
}
