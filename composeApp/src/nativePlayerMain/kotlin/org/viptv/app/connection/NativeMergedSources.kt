package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.fetchMergedLocalSources(profile: String, card: Card,
    publish: (SourcesOutcome) -> Unit): SourcesOutcome = coroutineScope {
    val prepared = prepareMetadataCard(profile, card)
    val gate = Mutex()
    val found = linkedMapOf<String, SourceCandidate>()
    val producers = linkedMapOf<String, SourceProducerOutcome>()
    suspend fun append(outcome: SourcesOutcome) = gate.withLock {
        currentCoroutineContext().ensureActive()
        outcome.candidates.forEach { found[it.id] = it }
        outcome.producers.forEach { producers[it.providerId] = it }
        publish(SourcesOutcome(found.values.toList(), producers = producers.values.toList()))
    }
    var stremioFailed = false
    var integrationFailed = false
    var torrentOnly = 0
    val stremio = async {
        try {
            val outcome = fetchLocalSources(profile, prepared, ::append)
            torrentOnly += outcome.torrentOnly
            append(SourcesOutcome(producers = outcome.producers))
        }
        catch (_: TimeoutCancellationException) { sourceFailureFact("addons", null); stremioFailed = true }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { sourceFailureFact("addons", error); stremioFailed = true }
    }
    val integrations = async {
        try { append(SourcesOutcome(fetchIntegrationSources(profile, prepared, ::append))) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { sourceFailureFact("integration", error); integrationFailed = true }
    }
    stremio.await(); integrations.await()
    if (found.isEmpty() && (stremioFailed || integrationFailed))
        throw LocalFailure(if (integrationFailed && !stremioFailed)
            "VIPTV Sources could not run. Check its TMDB key in Settings, then try again."
        else "Could not load sources. Check the configured providers and try again.")
    SourcesOutcome(found.values.toList(), if (found.isEmpty()) torrentOnly else 0, producers.values.toList())
}
