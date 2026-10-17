package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.ProfileConfiguration

private val integrationCodec = Json { ignoreUnknownKeys = true }

suspend fun RemoteConnection.sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot =
    integrationCodec.decodeFromJsonElement(call("/api/profiles/${profile.encodeURLPathPart()}/source-integrations"))

suspend fun RemoteConnection.configureSourceIntegration(profile: String, revision: Long, plugin: String, configuration: JsonObject) {
    call("/api/profiles/${profile.encodeURLPathPart()}/source-integrations", HttpMethod.Put, buildJsonObject {
        put("revision", revision)
        put("plugin_id", plugin)
        put("configuration", configuration)
    })
}

suspend fun RemoteConnection.removeSourceIntegration(profile: String, revision: Long, connection: String) {
    call("/api/profiles/${profile.encodeURLPathPart()}/source-integrations/${connection.encodeURLPathPart()}?revision=$revision", HttpMethod.Delete)
}
