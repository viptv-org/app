package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import org.viptv.app.connection.*

fun ApplicationController.connectIptv(path: String, credentials: IptvCredentials) {
    if (!credentials.complete) {
        mutable.value = mutable.value.copy(error = "Enter your provider address and required credentials.")
        return
    }
    startLocal(path, credentials)
}

fun ApplicationController.cancelIptvSetup() { disconnect() }

fun ApplicationController.cancelIptvEntry() {
    if (pendingIptv != null && mutable.value.screen == Screen.Connect) disconnect()
}

/** Runs after profile selection and its PIN check; nothing is assigned to other profiles. */
internal suspend fun ApplicationController.finishIptvSetup(profile: String): Boolean {
    val credentials = pendingIptv ?: return false
    val device = local ?: throw LocalFailure("Provider setup requires this device’s local connection.")
    if (mutable.value.screen == Screen.ProfilePin)
        mutable.value = mutable.value.copy(screen = Screen.Profiles, profileToOpen = null)
    val before = device.sourceIntegrationSchemas(profile)
    if (before.schemas.none { it.plugin_id == credentials.pluginId && it.runtime_enabled })
        throw LocalFailure("This provider type is unavailable on this device.")
    try { device.configureSourceIntegration(profile, before.revision, credentials.pluginId, credentials.configuration()) }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("Could not save this provider. Check the address and credentials, then try again.") }
    pendingIptv = null
    accept(device.bootstrap(profile), screen = Screen.Live, busy = true)
    loadSourceIntegrations()
    loadLive(LiveQuery())
    return true
}
