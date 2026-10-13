package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import org.viptv.app.connection.*

internal suspend fun ApplicationController.loadSourceIntegrations() {
    val profile = requireNotNull(mutable.value.selectedProfile)
    try {
        val result = local?.sourceIntegrationSchemas(profile) ?: requireNotNull(connection).sourceIntegrationSchemas(profile)
        mutable.value = mutable.value.copy(sourceIntegrations = result, sourceIntegrationError = null)
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) {
        mutable.value = mutable.value.copy(sourceIntegrationError = "Could not load source integrations. Try again.")
    }
}

fun ApplicationController.refreshSourceIntegrations() = run { loadSourceIntegrations() }

fun ApplicationController.configureSourceIntegration(plugin: String, configuration: JsonObject) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val revision = mutable.value.sourceIntegrations.revision
    local?.configureSourceIntegration(profile, revision, plugin, configuration)
        ?: requireNotNull(connection).configureSourceIntegration(profile, revision, plugin, configuration)
    loadSourceIntegrations()
}

fun ApplicationController.removeSourceIntegration(connectionId: String) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val revision = mutable.value.sourceIntegrations.revision
    local?.removeSourceIntegration(profile, revision, connectionId)
        ?: requireNotNull(connection).removeSourceIntegration(profile, revision, connectionId)
    loadSourceIntegrations()
}
