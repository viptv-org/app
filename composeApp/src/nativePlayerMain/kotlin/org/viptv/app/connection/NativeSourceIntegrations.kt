package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.readSourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot =
    withContext(Dispatchers.IO) { json.decodeFromString(engine.sourceIntegrationSchemas(profile)) }

internal suspend fun NativeLocalConnection.saveSourceIntegration(profile: String, revision: Long, plugin: String, configuration: JsonObject) {
    try { withContext(Dispatchers.IO) {
        engine.configureSourceIntegration(profile, revision.toULong(), plugin, configuration.toString())
    } } catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("Could not save source configuration. Refresh settings and check the credentials.") }
}

internal suspend fun NativeLocalConnection.deleteSourceIntegration(profile: String, revision: Long, connection: String) {
    try { withContext(Dispatchers.IO) { engine.removeSourceIntegration(profile, revision.toULong(), connection) } }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { throw LocalFailure("Could not remove source configuration. Refresh settings and try again.") }
}

internal suspend fun NativeLocalConnection.fetchIntegrationSources(profile: String, card: Card,
    publish: suspend (List<SourceCandidate>) -> Unit): List<SourceCandidate> {
    val snapshot = readSourceIntegrationSchemas(profile)
    if (snapshot.schemas.none { it.runtime_enabled && it.state == "Ready" }) return emptyList()
    var jobId: String? = null
    val found = linkedMapOf<String, SourceCandidate>()
    try {
        val completed = withTimeoutOrNull(45_000) {
            val started = withContext(Dispatchers.IO) {
                json.parseToJsonElement(engine.startIntegrationSources(profile, json.encodeToString(card))).jsonObject
            }
            jobId = started.getValue("id").jsonPrimitive.content
            var after = 0L
            while (true) {
                val page = withContext(Dispatchers.IO) {
                    json.decodeFromString<IntegrationSourcePage>(engine.pollIntegrationSources(profile, requireNotNull(jobId), after.toULong()))
                }
                page.events.forEach { event ->
                    event.sources.forEach { found[it.id] = it }
                    after = maxOf(after, event.seq)
                }
                if (page.events.isNotEmpty()) publish(found.values.toList())
                if (page.done) {
                    if (page.error_code != null && found.isEmpty()) throw LocalFailure("Source integration could not complete this title.")
                    break
                }
                delay(250)
            }
            true
        }
        if (completed == null && found.isEmpty()) throw LocalFailure("Source providers took too long. Try again.")
        return found.values.toList()
    } finally {
        jobId?.let { id -> withContext(NonCancellable + Dispatchers.IO) {
            withTimeoutOrNull(3_000) { try { engine.cancelIntegrationSources(profile, id) } catch (_: Exception) { } }
        } }
    }
}
