package org.viptv.app.connection

import kotlinx.serialization.json.*

internal fun JsonElement.resolveRemoteImages(origin: String): JsonElement = when (this) {
    is JsonObject -> {
        var rewritten: MutableMap<String, JsonElement>? = null
        for ((key, value) in this) {
            val resolved = value.resolveRemoteImages(origin)
            if (resolved !== value) {
                if (rewritten == null) rewritten = LinkedHashMap(this)
                rewritten[key] = resolved
            }
        }
        if (rewritten == null) this else JsonObject(rewritten)
    }
    is JsonArray -> {
        var rewritten: MutableList<JsonElement>? = null
        for (index in indices) {
            val resolved = this[index].resolveRemoteImages(origin)
            if (resolved !== this[index]) {
                if (rewritten == null) rewritten = toMutableList()
                rewritten[index] = resolved
            }
        }
        if (rewritten == null) this else JsonArray(rewritten)
    }
    is JsonPrimitive -> if (isString && content.startsWith("/api/images/")) JsonPrimitive(origin + content) else this
}
