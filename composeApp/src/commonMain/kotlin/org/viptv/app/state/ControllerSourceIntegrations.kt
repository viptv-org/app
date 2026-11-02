package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadSourceIntegrations(operation: AddonOperationOwner? = null) {
    val owner = operation ?: addonOperationOwner()
    owner.requireCurrent(this)
    val profile = requireNotNull(owner.profile)
    try {
        val result = owner.device?.sourceIntegrationSchemas(profile) ?: requireNotNull(owner.remote).sourceIntegrationSchemas(profile)
        owner.requireCurrent(this)
        updateState { next -> next.copy(sourceIntegrations = result, sourceIntegrationError = null) }
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) {
        owner.requireCurrent(this)
        if (error is RemoteFailure && error.status == 401) throw error
        updateState { next -> next.copy(sourceIntegrationError =
            if (error is RemoteFailure && error.status == 403) error.message
            else "Could not load source integrations. Try again.") }
    }
}

fun ApplicationController.refreshSourceIntegrations() = run { loadSourceIntegrations() }

fun ApplicationController.configureSourceIntegration(plugin: String, configuration: JsonObject) = run {
    val owner = addonOperationOwner()
    val profile = requireNotNull(owner.profile)
    val revision = mutable.value.sourceIntegrations.revision
    owner.device?.configureSourceIntegration(profile, revision, plugin, configuration)
        ?: requireNotNull(owner.remote).configureSourceIntegration(profile, revision, plugin, configuration)
    profileConfigurationChanged(owner)
}

fun ApplicationController.removeSourceIntegration(connectionId: String) = run {
    val owner = addonOperationOwner()
    val profile = requireNotNull(owner.profile)
    val revision = mutable.value.sourceIntegrations.revision
    owner.device?.removeSourceIntegration(profile, revision, connectionId)
        ?: requireNotNull(owner.remote).removeSourceIntegration(profile, revision, connectionId)
    profileConfigurationChanged(owner)
}
