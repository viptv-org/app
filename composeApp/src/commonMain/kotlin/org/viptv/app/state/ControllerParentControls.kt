package org.viptv.app.state

import org.viptv.app.connection.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin

fun ApplicationController.refreshParentControls() = run {
    mutable.value = mutable.value.copy(parentSettingsNotice = null)
    val operation = generation
    parentRequest { remote ->
        if (mutable.value.policyRefreshNeeded) {
            val profile = mutable.value.selectedProfile
            val snapshot = remote.bootstrap()
            if (connection === remote && generation == operation && mutable.value.selectedProfile == profile)
                accept(snapshot, if (mutable.value.screen == Screen.Settings) Screen.Settings else Screen.Home, busy = true)
            snapshot.profileManagement ?: remote.profileManagement()
        } else remote.profileManagement()
    }
}

fun ApplicationController.unlockParentControls(pin: String) {
    if (mutable.value.busy) return
    run {
        if (!validParentPin(pin)) throw LocalFailure("Enter a PIN with 4 to 8 digits.")
        parentRequest("Parent access unlocked temporarily.", "The parent PIN was not accepted. Check it and try again.") { remote ->
            remote.unlockParent(pin)
            remote.profileManagement()
        }
    }
}

fun ApplicationController.changeParentPin(current: String, next: String) {
    if (mutable.value.busy) return
    run {
        val status = mutable.value.parentSettings?.parent ?: throw LocalFailure("Refresh parental controls first.")
        if (status.pin_configured && !validParentPin(current)) throw LocalFailure("Enter the current parent PIN.")
        if (next.isNotEmpty() && !validParentPin(next)) throw LocalFailure("Use 4 to 8 digits for the new PIN.")
        if (next.isEmpty() && mutable.value.parentSettings?.profiles.orEmpty().any { it.kids })
            throw LocalFailure("Turn off kids restrictions on every profile before removing the parent PIN.")
        parentRequest(if (next.isEmpty()) "Parent PIN removed." else "Parent PIN saved.", "The current parent PIN was not accepted. Check it and try again.") { remote ->
            remote.saveParentPin(current, next)
            remote.profileManagement()
        }
    }
}

fun ApplicationController.changeKidsPolicy(profile: String, policy: KidsPolicy) {
    if (mutable.value.busy) return
    run {
        val settings = mutable.value.parentSettings ?: throw LocalFailure("Refresh parental controls first.")
        if (settings.profiles.none { it.id == profile }) throw LocalFailure("Choose one of this account’s profiles.")
        if (policy.enabled && !settings.parent.pin_configured) throw LocalFailure("Set a parent PIN before enabling kids restrictions.")
        if (policy.max_age !in 0..17) throw LocalFailure("Choose an age between 0 and 17.")
        val operation = generation
        parentRequest("Profile restrictions saved.") { remote ->
            if (mutable.value.selectedProfile == profile) {
                invalidatePolicyContent()
            }
            remote.saveKidsPolicy(profile, policy)
            if (mutable.value.selectedProfile == profile) {
                val snapshot = remote.bootstrap()
                if (connection === remote && generation == operation && mutable.value.selectedProfile == profile)
                    accept(snapshot, if (mutable.value.screen == Screen.Settings) Screen.Settings else Screen.Home, busy = true)
                snapshot.profileManagement ?: remote.profileManagement()
            } else remote.profileManagement()
        }
    }
}

private suspend fun ApplicationController.parentRequest(notice: String? = null, forbidden: String = "Unlock parental controls and try again.",
    action: suspend (RemoteConnection) -> ProfileManagement) {
    val remote = connection ?: throw LocalFailure("Manage this account through its server dashboard.")
    if (!remote.accountManagementEnabled) throw LocalFailure("Manage parental controls through your server’s web dashboard.")
    val owner = generation
    val saved = mutable.value.parentSettingsSaved
    try {
        val result = action(remote)
        if (connection === remote && generation == owner) mutable.value = mutable.value.copy(
            parentSettings = result, parentSettingsNotice = notice,
            parentSettingsSaved = saved + if (notice != null) 1 else 0,
            profiles = result.cards(), profileManagement = result)
    } catch (failure: RemoteFailure) {
        val refreshed = if (failure.status in setOf(403, 409)) try { remote.profileManagement() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            else mutable.value.parentSettings
        if (connection === remote && generation == owner) mutable.value = mutable.value.copy(parentSettingsNotice = null,
            parentSettings = refreshed)
        if (failure.status == 403) throw LocalFailure(forbidden)
        if (failure.status == 409) throw LocalFailure("These settings changed. Check the current profile restrictions and try again.")
        throw failure
    }
}

internal suspend fun ApplicationController.invalidatePolicyContent() {
    libraryHydration?.cancelAndJoin(); libraryHydration = null
    libraryImportRefresh?.cancelAndJoin(); libraryImportRefresh = null
    calendarReread?.cancelAndJoin(); calendarReread = null
    liveGuideJob?.cancelAndJoin(); liveGuideJob = null
    cancelDetailPaging()
    val current = mutable.value
    mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = current.selectedProfile,
        profiles = current.profiles, profileManagement = current.profileManagement,
        parentSettings = current.parentSettings, parentTitles = current.parentTitles,
        parentSettingsSaved = current.parentSettingsSaved, settingsPage = current.settingsPage, busy = true, policyRefreshNeeded = true)
}
