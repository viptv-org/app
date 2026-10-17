package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.*

private val addonCodec = Json { ignoreUnknownKeys = true }
data class RemoteAddons(val addons: List<IntegrationConnection>, val enabled: Set<String>, val browsing: BrowsingConfiguration? = null)
private data class RemoteConfiguration(val profile: ProfileConfiguration, val connections: List<IntegrationConnection>)

private suspend fun RemoteConnection.addonConfiguration(profile: String): RemoteConfiguration {
    val response = call("/api/profiles/${profile.encodeURLPathPart()}/integrations").jsonObject
    return RemoteConfiguration(addonCodec.decodeFromJsonElement(response.getValue("configuration")),
        addonCodec.decodeFromJsonElement(response.getValue("connections")))
}

/** Account inventory and profile assignments are separate server resources. */
suspend fun RemoteConnection.addons(profile: String): RemoteAddons {
    val config = addonConfiguration(profile)
    val inventory = mutableListOf<IntegrationConnection>()
    var cursor: String? = null
    val visited = mutableSetOf<String>()
    do {
        val page = call("/api/addons?limit=200" + (cursor?.let { "&cursor=${it.encodeURLParameter()}" } ?: "")).jsonObject
        page.getValue("items").jsonArray.forEach { element ->
            val row = element.jsonObject
            val id = "addon:${row.getValue("id").jsonPrimitive.content}"
            // Disabled account add-ons have no execution descriptor; their capabilities remain unknown.
            inventory += config.connections.find { it.id == id } ?: IntegrationConnection(id, "stremio",
                config.profile.scope.account_id, null, row.getValue("name").jsonPrimitive.content,
                row.getValue("enabled").jsonPrimitive.boolean, config.profile.scope.revision, emptyList())
        }
        cursor = page["next_cursor"]?.jsonPrimitive?.contentOrNull
        if (cursor != null && !visited.add(cursor)) throw RemoteFailure(502, "The server repeated an add-on page.")
    } while (cursor != null)
    val enabled = config.profile.assignments.filter { it.enabled.isNotEmpty() }.map { it.connection_id }.toSet()
    return RemoteAddons(inventory.distinctBy { it.id }, enabled, BrowsingConfiguration(config.profile, config.connections))
}

suspend fun RemoteConnection.installAddon(profile: String, address: String) {
    val row = call("/api/addons", HttpMethod.Post, buildJsonObject { put("manifest_url", address) }).jsonObject
    setAddonEnabled(profile, "addon:${row.getValue("id").jsonPrimitive.content}", true)
}

suspend fun RemoteConnection.setAddonEnabled(profile: String, id: String, enabled: Boolean) {
    addonNumericId(id)
    val config = addonConfiguration(profile)
    val connection = config.connections.find { it.id == id }
        ?: throw RemoteFailure(409, "This add-on is disabled for the account or is no longer available.")
    val assignments = config.profile.assignments.filter { it.connection_id != id && config.connections.any { c -> c.id == it.connection_id } }.toMutableList()
    if (enabled) assignments += ProfileAssignment(id, connection.capabilities)
    call("/api/profiles/${profile.encodeURLPathPart()}/integrations", HttpMethod.Put, buildJsonObject {
        put("revision", config.profile.scope.revision)
        put("browsing_plugin", config.profile.browsing_plugin)
        put("assignments", addonCodec.encodeToJsonElement(assignments))
    })
}

suspend fun RemoteConnection.removeAddon(id: String) {
    call("/api/addons/${addonNumericId(id)}", HttpMethod.Delete)
}

private fun addonNumericId(id: String): Long = id.removePrefix("addon:").toLongOrNull()
    ?.takeIf { id.startsWith("addon:") && it > 0 } ?: throw RemoteFailure(400, "Invalid add-on identifier.")
