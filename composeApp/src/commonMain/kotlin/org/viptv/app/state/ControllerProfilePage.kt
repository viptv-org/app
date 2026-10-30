package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshProfilePage() = run {
    val profile = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val remote = connection
    val owner = generation
    loadPreferences()
    if (remote != null) {
        val management = remote.profileManagement()
        val approvals = if (management.profiles.any { it.id == profile && it.kids }) remote.approvedTitles(profile).size else null
        if (connection === remote && generation == owner && mutable.value.selectedProfile == profile)
            updateState { next -> next.copy(profiles = management.cards(), profileManagement = management,
                parentSettings = management, profileApprovals = approvals) }
    }
}

fun ApplicationController.saveProfileName(name: String, pin: String) = run {
    val id = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val trimmed = name.trim()
    if (trimmed.isEmpty()) throw LocalFailure("Enter a name for this profile.")
    if (local != null) requireNotNull(local).renameProfile(id, trimmed, pin) else requireNotNull(connection).renameProfile(id, trimmed)
    finishProfilePageSave()
}

fun ApplicationController.setSelectedProfileAvatar(style: String, choice: Int, pin: String) = run {
    val id = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).setProfileAvatar(id, style, choice, pin)
    else requireNotNull(connection).setProfileAvatar(id, style, choice)
    finishProfilePageSave()
}

fun ApplicationController.setSelectedProfilePin(oldPin: String, newPin: String) = run {
    val id = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val device = local ?: throw LocalFailure("This server uses a household PIN. Open Parental controls to manage it.")
    device.setProfilePin(id, oldPin, newPin)
    finishProfilePageSave()
}

fun ApplicationController.saveProfileKids(enabled: Boolean, maxAge: Int, pin: String) = run {
    val id = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    val remote = connection ?: throw LocalFailure("Connect to your account to manage kids restrictions.")
    if (maxAge !in 0..17) throw LocalFailure("Choose an age between 0 and 17.")
    val parent = (mutable.value.profileManagement ?: remote.profileManagement()).parent
    if (enabled && !parent.pin_configured) throw LocalFailure("Set a household PIN before turning on Kids profile.")
    if (enabled && parent.pin_configured && !parent.unlocked) {
        if (!validParentPin(pin)) throw LocalFailure("Enter the household PIN to change this profile’s limits.")
        remote.unlockParent(pin)
    }
    val owner = generation
    val page = mutable.value.settingsPage
    if (mutable.value.selectedProfile == id) invalidatePolicyContent()
    remote.saveKidsPolicy(id, KidsPolicy(enabled, maxAge))
    val approvals = if (enabled) remote.approvedTitles(id).size else null
    val snapshot = remote.bootstrap()
    if (connection === remote && generation == owner) {
        accept(snapshot, screen = Screen.Settings)
        updateState { next -> next.copy(settingsPage = page, selectedProfile = id, profileApprovals = approvals,
            profilePageSaved = next.profilePageSaved + 1) }
    }
}

fun ApplicationController.deleteSelectedProfile(pin: String) = run {
    val id = mutable.value.selectedProfile ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).deleteProfile(id, pin) else requireNotNull(connection).deleteProfile(id)
    val next = local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    progressJob?.cancel(); subtitleDiscovery?.cancel(); liveGuideJob?.cancel()
    liveFavoriteJobs.values.forEach { it.cancel() }; liveFavoriteJobs.clear()
    releaseRemotePlayback()?.join()
    accept(next, screen = Screen.Profiles)
    updateState { current -> current.copy(managingProfiles = true) }
}

private suspend fun ApplicationController.finishProfilePageSave() {
    val remote = connection
    val device = local
    val next = device?.bootstrap() ?: requireNotNull(remote).bootstrap()
    if (connection === remote && local === device)
        updateState { current -> current.copy(profiles = next.profiles, profileManagement = next.profileManagement,
            profilePageSaved = current.profilePageSaved + 1) }
}