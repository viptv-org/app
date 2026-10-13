package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import org.viptv.engine.wire.HomeSnapshot

internal suspend fun NativeLocalConnection.enrichHome(profile: String, snapshot: HomeSnapshot): HomeSnapshot {
    val plan = withContext(Dispatchers.IO) {
        json.parseToJsonElement(engine.planHomeEnrichment(profile, json.encodeToString(snapshot))).jsonObject
    }
    val capacity = Semaphore(3)
    val results = coroutineScope {
        plan.getValue("requests").jsonArray.map { request -> async {
            val fields = request.jsonObject
            val body = try { withTimeout(1900) { capacity.withPermit { addonJson(client, fields.getValue("url").jsonPrimitive.content) } } }
            catch (_: TimeoutCancellationException) { null }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            buildJsonObject {
                put("operation", fields.getValue("operation"))
                body?.let { put("body", it) }
            }
        } }.awaitAll()
    }
    return withContext(Dispatchers.IO) {
        json.decodeFromString(engine.projectHomeEnrichment(plan.getValue("operation").jsonPrimitive.content, JsonArray(results).toString()))
    }
}
