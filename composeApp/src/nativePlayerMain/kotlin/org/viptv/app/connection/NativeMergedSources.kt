package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.fetchMergedLocalSources(profile: String, card: Card,
    publish: (SourcesOutcome) -> Unit, context: RankingContext = RankingContext()): SourcesOutcome = coroutineScope {
    val began = System.nanoTime()
    var preferredBudget: Long? = null
    var excludedAddon: String? = null
    var preparedCard: Card? = null
    if (context.reuse.present) {
        val plan = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.rankSourcesWithContext(profile, "[]", rankingContextJson(context).toString())).jsonObject }
        preferredBudget = plan.getValue("preferred_source_budget_ms").jsonPrimitive.long
        val memory = plan["preferred_source_memory"]?.takeUnless { it is JsonNull }?.jsonObject
        val provider = memory?.get("provider_id")?.jsonPrimitive?.contentOrNull
        val sdk = readSourceIntegrationSchemas(profile).connections.any { it.id == provider && it.plugin_id == "vsources" }
        if (provider != null && !sdk) {
            excludedAddon = provider
            var preferred: SourcesOutcome? = null
            if (!context.excludePreferred) withTimeoutOrNull((requireNotNull(preferredBudget) - (System.nanoTime() - began) / 1_000_000).coerceAtLeast(1)) {
                val prepared = prepareMetadataCard(profile, card).also { preparedCard = it }
                try {
                    fetchLocalSources(profile, prepared, preferredProvider = provider, publish = { outcome ->
                        val ranked = rankSources(profile, outcome.candidates, context)
                        if (ranked.reuseReady && ranked.reusePreferred) { preferred = outcome.copy(discoveryPhase = "preferred"); throw PreferredSourceFound() }
                    })
                } catch (_: PreferredSourceFound) { }
            }
            preferred?.let { publish(it); return@coroutineScope it }
        }
    }
    val fallback = CompletableDeferred<Boolean>()
    if (!context.reuse.present || excludedAddon != null) fallback.complete(true)
    val prepared = preparedCard ?: preferredBudget?.let { budget -> withTimeoutOrNull((budget - (System.nanoTime() - began) / 1_000_000).coerceAtLeast(1)) { prepareMetadataCard(profile, card) } }
        ?: prepareMetadataCard(profile, card)
    val activeContext = context.copy(preferredBudgetMillis = preferredBudget?.let { (it - (System.nanoTime() - began) / 1_000_000).coerceAtLeast(0) })
    if (activeContext.preferredBudgetMillis == 0L) fallback.complete(true)
    val gate = Mutex()
    val found = linkedMapOf<String, SourceCandidate>()
    val producers = linkedMapOf<String, SourceProducerOutcome>()
    var phase: String? = null
    suspend fun append(outcome: SourcesOutcome) = gate.withLock {
        currentCoroutineContext().ensureActive()
        phase = outcome.discoveryPhase ?: phase
        outcome.candidates.forEach { found[it.id] = it }
        outcome.producers.forEach { producers[it.providerId] = it }
        publish(SourcesOutcome(found.values.toList(), producers = producers.values.toList(), discoveryPhase = phase))
    }
    var stremioFailed = false
    var integrationFailed = false
    var torrentOnly = 0
    val stremio = async {
        try {
            if (!fallback.await()) return@async
            gate.withLock { phase = "fallback" }
            val outcome = fetchLocalSources(profile, prepared, preferredProvider = null, excludedProvider = excludedAddon, publish = ::append)
            torrentOnly += outcome.torrentOnly
            append(SourcesOutcome(producers = outcome.producers))
        }
        catch (_: TimeoutCancellationException) { sourceFailureFact("addons", null); stremioFailed = true }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { sourceFailureFact("addons", error); stremioFailed = true }
    }
    val integrations = async {
        try { append(SourcesOutcome(fetchIntegrationSources(profile, prepared, ::append, activeContext) { fallback.complete(true) })) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { sourceFailureFact("integration", error); integrationFailed = true }
        finally { fallback.complete(found.isEmpty()) }
    }
    stremio.await(); integrations.await()
    if (found.isEmpty() && (stremioFailed || integrationFailed))
        throw LocalFailure(if (integrationFailed && !stremioFailed)
            "VIPTV Sources could not run. Check its TMDB key in Settings, then try again."
        else "Could not load sources. Check the configured providers and try again.")
    SourcesOutcome(found.values.toList(), if (found.isEmpty()) torrentOnly else 0, producers.values.toList(), phase ?: "fallback")
}

private class PreferredSourceFound : Exception()
