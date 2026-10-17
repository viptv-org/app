package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.*

data class BrowsingConfiguration(val profile: ProfileConfiguration, val connections: List<IntegrationConnection>)

suspend fun RemoteConnection.selectBrowsingProvider(profile: String, current: BrowsingConfiguration, plugin: String) {
    val selected = current.connections.filter { it.enabled && it.plugin_id == plugin && Capability.DISCOVERY in it.capabilities }
    if (selected.isEmpty()) throw RemoteFailure(409, "Configure this metadata provider first.")
    val assignments = current.profile.assignments.toMutableList()
    selected.forEach { connection ->
        val old = assignments.find { it.connection_id == connection.id }
        val browsing = connection.capabilities.filter { it in listOf(Capability.DISCOVERY, Capability.SEARCH, Capability.METADATA) }
        assignments.removeAll { it.connection_id == connection.id }
        assignments += ProfileAssignment(connection.id, (old?.enabled.orEmpty() + browsing).distinct())
    }
    call("/api/profiles/${profile.encodeURLPathPart()}/integrations", HttpMethod.Put, buildJsonObject {
        put("revision", current.profile.scope.revision)
        put("browsing_plugin", plugin)
        put("assignments", Json.encodeToJsonElement(assignments))
    })
}
