package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

internal suspend fun fetchLocalCatalogRows(requests: JsonArray, timeoutMillis: Long = 6000, fetch: suspend (String) -> String): JsonArray = coroutineScope {
    val capacity = Semaphore(3)
    val responses = requests.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }.distinct().associateWith { url ->
        async {
            val started = System.nanoTime()
            try { withTimeout(timeoutMillis) { capacity.withPermit { fetch(url) } } }
            catch (_: TimeoutCancellationException) { addonFetchFact("catalog", 0, started, error = "timeout"); null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { addonFetchFact("catalog", 0, started, error = error::class.simpleName); null }
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
