package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*

internal const val passwordChangedNotice = "Password changed. Other devices stay signed in."

fun ApplicationController.refreshPasswordChangeAvailability() {
    val remote = connection ?: return
    scope.launch {
        val available = try { remote.passwordChangeAvailable() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@launch }
        // The answer belongs to the connection, not to a run: Settings' own loads bump the generation while this is in flight.
        if (connection === remote) updateState { it.copy(passwordChangeAvailable = available) }
    }
}

fun ApplicationController.passwordChanged() {
    updateState { it.copy(passwordChangeNotice = passwordChangedNotice) }
    showSettingsPage(SettingsPage("Account"))
}
