package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.selectProfile(id: String) {
    if (mutable.value.profiles.any { it.id == id && it.locked }) {
        mutable.value = mutable.value.copy(screen = Screen.ProfilePin, profileToOpen = id, error = null)
        return
    }
    run { openProfile(id) }
}

fun ApplicationController.submitProfilePin(pin: String) = run {
    val id = mutable.value.profileToOpen ?: throw LocalFailure("Choose a profile first.")
    if (local != null) {
        if (!requireNotNull(local).verifyProfilePin(id, pin)) throw LocalFailure("Incorrect PIN or too many attempts. Try again later.")
    } else requireNotNull(connection).unlockParent(pin)
    openProfile(id)
}

internal suspend fun ApplicationController.openProfile(id: String) {
    if (!completionSeen) saveProgress(false, final = true)?.join() else progressJob?.join()
    releaseRemotePlayback()?.join()
    if (finishIptvSetup(id)) return
    val result=local?.bootstrap(id) ?: requireNotNull(connection).selectProfile(id)
    accept(result)
    local?.let { mutable.value=mutable.value.copy(home=it.refreshHome(id)) }
}

fun ApplicationController.manageProfiles() { mutable.value=mutable.value.copy(managingProfiles=true,error=null) }

fun ApplicationController.stopManagingProfiles() { mutable.value=mutable.value.copy(managingProfiles=false,error=null) }

fun ApplicationController.editProfile(id: String) { mutable.value=mutable.value.copy(screen=Screen.ProfileEdit,profileToEdit=id,error=null) }

fun ApplicationController.createProfile(name: String) = run {
    if (local != null) requireNotNull(local).createProfile(name) else requireNotNull(connection).createProfile(name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,error=null)
}

fun ApplicationController.renameProfile(name: String, pin: String) = run {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).renameProfile(id,name,pin) else requireNotNull(connection).renameProfile(id,name)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true)
}

fun ApplicationController.setProfileAvatar(avatarId: String, pin: String) = run {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).setProfileAvatar(id,avatarId,pin) else requireNotNull(connection).setProfileAvatar(id,avatarId)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement)
}

fun ApplicationController.deleteProfile(pin: String) = run {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    if (local != null) requireNotNull(local).deleteProfile(id,pin) else requireNotNull(connection).deleteProfile(id)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true,
        selectedProfile=mutable.value.selectedProfile.takeUnless { it==id })
}

fun ApplicationController.setProfilePin(oldPin: String, newPin: String) = run {
    val id=mutable.value.profileToEdit ?: throw LocalFailure("Choose a profile first.")
    requireNotNull(local).setProfilePin(id,oldPin,newPin)
    val next=local?.bootstrap() ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(profiles=next.profiles, profileManagement=next.profileManagement,screen=Screen.Profiles,managingProfiles=true)
}
