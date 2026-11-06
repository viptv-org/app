package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.selectProfile(id: String) {
    if (mutable.value.busy || mutable.value.mutations.running(MutationKind.Profile)) return
    if (mutable.value.profiles.any { it.id == id && it.locked }) {
        updateState { next -> next.copy(screen = Screen.ProfilePin, profileToOpen = id, leavePinFrom = null, error = null) }
        return
    }
    if (householdSwitchNeedsPin(id)) {
        updateState { next -> next.copy(householdPinFor = id, error = null, errorCode = null) }
        return
    }
    run { openProfile(id) }
}

private fun ApplicationController.householdSwitchNeedsPin(id: String): Boolean {
    val state = mutable.value
    val parent = state.profileManagement?.parent ?: return false
    return connection != null && parent.restricted && parent.pin_configured && id != state.selectedProfile
}

fun ApplicationController.submitHouseholdSwitch(pin: String) {
    val target = mutable.value.householdPinFor ?: return
    if (!validParentPin(pin)) throw LocalFailure("Enter a PIN with 4 to 8 digits.")
    run {
        requireNotNull(connection).unlockParent(pin)
        updateState { it.copy(householdPinFor = null) }
        openProfile(target)
    }
}

fun ApplicationController.cancelHouseholdSwitch() {
    updateState { it.copy(householdPinFor = null, error = null, errorCode = null) }
}

fun ApplicationController.submitProfilePin(pin: String) = run {
    val id = mutable.value.profileToOpen ?: throw LocalFailure("Choose a profile first.")
    if (mutable.value.leavePinFrom != null) {
        if (!validParentPin(pin)) throw LocalFailure("Enter a PIN with 4 to 8 digits.")
        openProfile(id, pin)
        return@run
    }
    if (local != null) {
        if (!requireNotNull(local).verifyProfilePin(id, pin)) throw LocalFailure("Incorrect PIN or too many attempts. Try again later.")
    } else requireNotNull(connection).unlockParent(pin)
    openProfile(id)
}

internal fun ApplicationController.beginHomeTransition(profile: String) {
    updateState { next -> next.copy(screen = Screen.Home, home = null, profileToOpen = profile,
        homeGenres = null, homeRowBusy = emptySet(), homeRowErrors = emptyMap(), error = null) }
}

internal suspend fun ApplicationController.openProfile(id: String, pin: String? = null) {
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releasePlaybackSession()?.join()
    val device = local
    val result = if (device != null) {
        beginHomeTransition(id)
        device.bootstrap(id)
    } else switchRemoteProfile(id, pin) ?: return
    restartRequested = false
    accept(result)
    if (device != null) {
        val home = device.refreshHome(id)
        updateState { next -> next.copy(home = home) }
    }
    resumeNavigation()
}

private suspend fun ApplicationController.switchRemoteProfile(id: String, pin: String?): Bootstrap? {
    val remote = requireNotNull(connection)
    // The switch is validated before the slow Home bootstrap, so a rejected PIN never shows Home.
    try {
        remote.switchProfile(id, pin)
    } catch (failure: RemoteFailure) {
        when {
            failure.code == "leave_pin_required" -> {
                updateState { next -> next.copy(screen = Screen.ProfilePin, profileToOpen = id,
                    leavePinFrom = next.selectedProfile, error = null) }
                return null
            }
            failure.code == "leave_pin_invalid" -> throw LocalFailure("That PIN was not accepted. Try again.")
            failure.code == "parent_required" -> {
                if (mutable.value.screen != Screen.Profiles) throw failure
                updateState { next -> next.copy(householdPinFor = id, error = null, errorCode = null) }
                return null
            }
            failure.status == 429 -> throw LocalFailure("Too many PIN attempts. Try again in five minutes.")
            else -> throw failure
        }
    }
    beginHomeTransition(id)
    return remote.bootstrap()
}

fun ApplicationController.manageProfiles() { updateState { next -> next.copy(managingProfiles=true,error=null) } }

fun ApplicationController.stopManagingProfiles() { updateState { next -> next.copy(managingProfiles=false,error=null) } }

fun ApplicationController.editProfile(id: String) { updateState { next -> next.copy(screen=Screen.ProfileEdit,profileToEdit=id,error=null) } }

fun ApplicationController.createProfile(name: String) = profileMutation(PROFILE_CREATE) {
    if (local != null) requireNotNull(local).createProfile(name) else requireNotNull(connection).createProfile(name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    updateState { current -> current.copy(profiles=next.profiles, profileManagement=next.profileManagement,error=null) }
}

fun ApplicationController.renameProfile(name: String, pin: String) = profileMutation(PROFILE_RENAME) {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).renameProfile(id,name,pin) else requireNotNull(connection).renameProfile(id,name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    updateState { current -> current.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true) }
}

fun ApplicationController.setProfileAvatar(style: String, choice: Int, pin: String) = profileMutation(PROFILE_AVATAR) {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).setProfileAvatar(id,style,choice,pin) else requireNotNull(connection).setProfileAvatar(id,style,choice)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    updateState { current -> current.copy(profiles=next.profiles, profileManagement=next.profileManagement) }
}

fun ApplicationController.deleteProfile(pin: String) = profileMutation(PROFILE_DELETE) {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).deleteProfile(id,pin) else requireNotNull(connection).deleteProfile(id)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    if (mutable.value.selectedProfile == id) {
        progressJob?.cancel(); subtitleDiscovery?.cancel(); liveGuideJob?.cancel()
        cancelLiveFavoriteMutations()
        releaseRemotePlayback()?.join()
        accept(next, screen = Screen.Profiles)
        updateState { next -> next.copy(managingProfiles = true) }
    } else updateState { current -> current.copy(profiles=next.profiles, profileManagement=next.profileManagement,
        screen=Screen.Profiles,managingProfiles=true,profileToEdit=null,profileToOpen=null) }
}

fun ApplicationController.setProfilePin(oldPin: String, newPin: String) = profileMutation(PROFILE_PIN) {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    val device = local ?: throw LocalFailure("This server uses an account parent PIN. Open Parental controls to manage it.")
    device.setProfilePin(id,oldPin,newPin)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    updateState { current -> current.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true) }
}

// The dialog that was confirmed names the write, so its button draws the pending state and a repeat press is dropped.
internal fun ApplicationController.profileMutation(action: String, operation: suspend () -> Unit) {
    // One profile write at a time: a second dialog must not stack on, or replace, the write already running.
    if (mutable.value.mutations.running(MutationKind.Profile)) return
    mutate(profileMutationTarget(action), ownsControllerJob = true, operation = operation)
}

internal fun profileMutationTarget(action: String) = MutationTarget(MutationKind.Profile, action)

internal const val PROFILE_CREATE = "Create profile"
internal const val PROFILE_RENAME = "Rename profile"
internal const val PROFILE_AVATAR = "Set avatar"
internal const val PROFILE_DELETE = "Delete profile"
internal const val PROFILE_PIN = "Profile PIN"

fun ApplicationController.profileSettings(section: String) {
    if (mutable.value.busy || section !in listOf("Subtitles & audio", "Parental controls")) return
    if (section == "Subtitles & audio" && mutable.value.profileToEdit != mutable.value.selectedProfile) {
        updateState { next -> next.copy(screen = Screen.Profiles, managingProfiles = false, error = null, settingsEntrySection = null) }
        return
    }
    updateState { next -> next.copy(settingsEntrySection = section) }
    navigate(Screen.Settings)
    if (section == "Subtitles & audio" && mutable.value.preferences == null) refreshPreferences()
}
