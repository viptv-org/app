package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

internal suspend fun fetchLocalCatalogRows(requests: JsonArray, fetch: suspend (String) -> String): JsonArray = coroutineScope {
    val capacity = Semaphore(3)
    val responses = requests.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }.distinct().associateWith { url ->
        async {
            try { capacity.withPermit { withTimeout(1900) { fetch(url) } } }
            catch (_: TimeoutCancellationException) { null }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
        }
    }
    JsonArray(requests.map { item ->
        val row = item.jsonObject
        val url = row["url"]?.jsonPrimitive?.contentOrNull
        val body = url?.let { responses.getValue(it).await() }
        buildJsonObject {
            put("id", row.getValue("id").jsonPrimitive.content)
            body?.let { put("body", it) }
        }
    })
}
