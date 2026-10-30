package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.refreshAccountDeletion() = run {
    val remote = connection ?: throw LocalFailure("Connect to your account to delete it.")
    val owner = generation
    val identity = remote.accountIdentity()
    val management = remote.profileManagement()
    if (connection === remote && generation == owner)
        updateState { next -> next.copy(accountIdentity = identity, parentSettings = management) }
}

fun ApplicationController.deleteAccount(username: String, password: String, pin: String?) = run {
    val remote = connection ?: throw LocalFailure("Connect to your account to delete it.")
    val owner = generation
    try {
        remote.deleteAccount(username, password, pin?.takeIf(String::isNotEmpty))
    } catch (failure: RemoteFailure) {
        if (failure.status == 401) throw failure
        throw LocalFailure(accountDeletionFailure(failure))
    }
    if (connection !== remote || generation != owner) return@run
    remote.forgetDeletedAccount()
    clearDeletedSession(remote)
}

internal fun accountDeletionFailure(failure: RemoteFailure): String = when {
    failure.code == "last_owner" -> "Other accounts still need an owner. Have them delete their accounts first."
    failure.code == "account_delete_rejected" -> "The username or password is not correct."
    failure.code == "parent_pin_incorrect" -> "The parent PIN is not correct."
    failure.code == "account_changed" -> "This account changed. Try again."
    failure.code == "account_session_required" -> "Manage this from a signed-in phone, computer or the web dashboard."
    failure.status == 429 -> failure.message ?: "Too many attempts. Wait a minute and try again."
    else -> "Could not delete your account. Nothing was changed. Try again."
}

private fun ApplicationController.clearDeletedSession(remote: RemoteConnection) {
    discardStremioImport()
    cancelDevicePairing()
    libraryImportRefresh?.cancel(); libraryImportRefresh = null
    calendarReread?.cancel(); calendarReread = null
    libraryHydration?.cancel(); libraryHydration = null
    progressJob?.cancel(); progressJob = null
    quietJob?.cancel(); quietJob = null
    freshness.reset()
    cancelDetailPaging()
    resetLiveRequests()
    releaseRemotePlayback()
    connection = null
    remote.close()
    mutable.value = ApplicationState(error = "Your account was deleted.")
}