package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.selectProfile(id: String) {
    if (mutable.value.busy) return
    if (mutable.value.profiles.any { it.id == id && it.locked }) {
        mutable.value = mutable.value.copy(screen = Screen.ProfilePin, profileToOpen = id, error = null)
        return
    }
    run { openProfile(id) }
}

fun ApplicationController.submitProfilePin(pin: String) = profileMutation {
    val id = mutable.value.profileToOpen ?: throw LocalFailure("Choose a profile first.")
    if (local != null) {
        if (!requireNotNull(local).verifyProfilePin(id, pin)) throw LocalFailure("Incorrect PIN or too many attempts. Try again later.")
    } else requireNotNull(connection).unlockParent(pin)
    openProfile(id)
}

internal suspend fun ApplicationController.openProfile(id: String) {
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releaseRemotePlayback()?.join()
    val result=local?.bootstrap(id) ?: requireNotNull(connection).selectProfile(id)
    // Playback intent belongs to the profile that set it; a switch must not drag
    // "start from the beginning" into the next profile's resume position.
    restartRequested = false
    accept(result)
    local?.let { mutable.value=mutable.value.copy(home=it.refreshHome(id)) }
}

fun ApplicationController.manageProfiles() { mutable.value=mutable.value.copy(managingProfiles=true,error=null) }

fun ApplicationController.stopManagingProfiles() { mutable.value=mutable.value.copy(managingProfiles=false,error=null) }

fun ApplicationController.editProfile(id: String) { mutable.value=mutable.value.copy(screen=Screen.ProfileEdit,profileToEdit=id,error=null) }

fun ApplicationController.createProfile(name: String) = profileMutation {
    if (local != null) requireNotNull(local).createProfile(name) else requireNotNull(connection).createProfile(name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,error=null)
}

fun ApplicationController.renameProfile(name: String, pin: String) = profileMutation {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).renameProfile(id,name,pin) else requireNotNull(connection).renameProfile(id,name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true)
}

/** Saves a design catalog pick ([style], 1-based [choice]) for the profile being edited. */
fun ApplicationController.setProfileAvatar(style: String, choice: Int, pin: String) = profileMutation {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).setProfileAvatar(id,style,choice,pin) else requireNotNull(connection).setProfileAvatar(id,style,choice)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement)
}

fun ApplicationController.deleteProfile(pin: String) = profileMutation {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).deleteProfile(id,pin) else requireNotNull(connection).deleteProfile(id)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    if (mutable.value.selectedProfile == id) {
        progressJob?.cancel(); subtitleDiscovery?.cancel(); liveGuideJob?.cancel()
        liveFavoriteJobs.values.forEach { it.cancel() }; liveFavoriteJobs.clear()
        releaseRemotePlayback()?.join()
        accept(next, screen = Screen.Profiles)
        mutable.value = mutable.value.copy(managingProfiles = true)
    } else mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,
        screen=Screen.Profiles,managingProfiles=true,profileToEdit=null,profileToOpen=null)
}

fun ApplicationController.setProfilePin(oldPin: String, newPin: String) = profileMutation {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    val device = local ?: throw LocalFailure("This server uses an account parent PIN. Open Parental controls to manage it.")
    device.setProfilePin(id,oldPin,newPin)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true)
}

/** Do not cancel an already submitted mutation just because a stale UI action repeats. */
private fun ApplicationController.profileMutation(operation: suspend () -> Unit) {
    if (mutable.value.busy) return
    run(operation)
}

/** Existing settings operate on the selected profile, never an implicit edit target. */
fun ApplicationController.profileSettings(section: String) {
    if (mutable.value.busy || section !in listOf("Subtitles & audio", "Parental controls")) return
    if (section == "Subtitles & audio" && mutable.value.profileToEdit != mutable.value.selectedProfile) {
        mutable.value = mutable.value.copy(screen = Screen.Profiles, managingProfiles = false, error = null, settingsEntrySection = null)
        return
    }
    mutable.value = mutable.value.copy(settingsEntrySection = section)
    navigate(Screen.Settings)
    if (section == "Subtitles & audio" && mutable.value.preferences == null) refreshPreferences()
}
