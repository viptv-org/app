package org.viptv.app.connection

import kotlinx.serialization.json.*

/** Image references follow the connected API, including a LAN testing facade. */
internal fun JsonElement.resolveRemoteImages(origin: String): JsonElement = when (this) {
    is JsonObject -> JsonObject(mapValues { it.value.resolveRemoteImages(origin) })
    is JsonArray -> JsonArray(map { it.resolveRemoteImages(origin) })
    is JsonPrimitive -> if (isString && content.startsWith("/api/images/")) JsonPrimitive(origin + content) else this
}
