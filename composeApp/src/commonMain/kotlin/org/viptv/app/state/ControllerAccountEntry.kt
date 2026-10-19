package org.viptv.app.state

import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*

fun ApplicationController.registerAccount(origin: String, username: String, name: String, password: String) =
    accountEntry(origin, username, name, password, null)

fun ApplicationController.recoverAccount(origin: String, username: String, code: String, password: String) =
    accountEntry(origin, username, null, password, code)

private fun ApplicationController.accountEntry(origin: String, username: String, name: String?, password: String, code: String?) {
    if (mutable.value.busy || mutable.value.recoveryKit != null) return
    run {
        if (!validAccountUsername(username)) throw LocalFailure("Use a username with letters, numbers, dots, hyphens, underscores or @.")
        if (!validNewPassword(password)) throw LocalFailure("Choose a longer password, within the server’s password limit.")
        if (name != null && (name.trim().isEmpty() || name.trim().encodeToByteArray().size > 80)) throw LocalFailure("Enter a shorter display name.")
        if (code != null && (code.trim().isEmpty() || code.length > 256)) throw LocalFailure("Enter your account recovery code.")
        val remote = connection ?: browserOrigin()?.let {
            RemoteConnection(origin, sessionStore = sessionStore).also { created -> connection = created }
        } ?: throw LocalFailure("Create or recover accounts through your server’s web sign-in page.")
        if (!remote.accountManagementEnabled) throw LocalFailure("Create or recover accounts through your server’s web sign-in page.")
        val owner = generation
        try {
            val kit = if (code == null) remote.registerAccount(username, requireNotNull(name), password)
                else remote.recoverAccount(username, code, password)
            // No bootstrap or metadata requests can discard the one-time codes.
            if (connection === remote && generation == owner) mutable.value = ApplicationState(
                screen = Screen.RecoveryCodes, recoveryKit = kit, busy = true)
        } catch (failure: RemoteFailure) {
            throw LocalFailure(when (failure.status) {
                409 -> "That username is unavailable. Choose another."
                401 -> "The username and recovery code did not match."
                429 -> "Too many attempts. Wait a moment and try again."
                else -> ServerMessages.human(failure.message, ServerMessages.GENERIC)
            })
        }
    }
}

/** Safe to retry: acknowledging codes only loads the existing session. */
fun ApplicationController.finishAccountEntry() {
    if (mutable.value.busy || mutable.value.recoveryKit == null) return
    run {
        val remote = requireNotNull(connection)
        val owner = generation
        val result = try { remote.bootstrap() } catch (failure: RemoteFailure) {
            throw LocalFailure(if (failure.status == 401) "Save your recovery code, then sign in again." else ServerMessages.human(failure.message, ServerMessages.GENERIC))
        }
        if (connection === remote && generation == owner) accept(result)
    }
}

