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
    publish: suspend (SourcesOutcome) -> Unit, context: RankingContext = RankingContext(),
    fallback: () -> Unit = {}): List<SourceCandidate> {
    val snapshot = readSourceIntegrationSchemas(profile)
    val ready = snapshot.schemas.filter { it.runtime_enabled && it.state == "Ready" }
    if (ready.isEmpty()) { fallback(); return emptyList() }
    val names = snapshot.connections.associate { it.id to it.name }
    val producers = linkedMapOf<String, SourceProducerOutcome>()
    ready.forEach { schema ->
        val id = schema.connection_id ?: return@forEach
        producers[id] = SourceProducerOutcome(id, names[id] ?: schema.name)
    }
    var jobId: String? = null
    val found = linkedMapOf<String, SourceCandidate>()
    var phase: String? = null
    fun published() = SourcesOutcome(found.values.toList(), producers = producers.values.toList(), discoveryPhase = phase)
    try {
        val completed = withTimeoutOrNull(45_000) {
            val started = withContext(Dispatchers.IO) {
                json.parseToJsonElement(engine.startIntegrationSources(profile, JsonObject(json.encodeToJsonElement(card).jsonObject + ("discovery" to buildJsonObject {
                    context.reuse.json(true).forEach { (key, value) -> put(key, value) }
                    if (context.excludePreferred) put("exclude_preferred_source", true)
                    context.preferredBudgetMillis?.let { put("preferred_budget_ms", it) }
                    put("ranking", rankingContextJson(context.copy(reuse = SourceReuseContext())))
                })).toString())).jsonObject
            }
            jobId = started.getValue("id").jsonPrimitive.content
            if (producers.isNotEmpty()) publish(published())
            var after = 0L
            while (true) {
                val page = withContext(Dispatchers.IO) {
                    json.decodeFromString<IntegrationSourcePage>(engine.pollIntegrationSources(profile, requireNotNull(jobId), after.toULong()))
                }
                val phaseChanged = phase != page.phase
                phase = page.phase
                if (page.phase == "fallback") fallback()
                page.events.forEach { event ->
                    event.sources.forEach { found[it.id] = it }
                    after = maxOf(after, event.seq)
                }
                if (page.events.isNotEmpty() || phaseChanged) publish(published())
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
        jobId?.let { id -> withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(3_000) { try { engine.cancelIntegrationSources(profile, id) } catch (_: Exception) { } }
            }
        } }
    }
}
