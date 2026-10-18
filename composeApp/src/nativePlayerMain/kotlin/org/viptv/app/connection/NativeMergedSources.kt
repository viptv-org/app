package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.fetchMergedLocalSources(profile: String, card: Card,
    publish: (List<SourceCandidate>) -> Unit): SourcesOutcome = coroutineScope {
    val prepared = prepareMetadataCard(profile, card)
    val gate = Mutex()
    val found = linkedMapOf<String, SourceCandidate>()
    suspend fun append(cards: List<SourceCandidate>) = gate.withLock {
        currentCoroutineContext().ensureActive()
        cards.forEach { found[it.id] = it }
        publish(found.values.toList())
    }
    var stremioFailed = false
    var integrationFailed = false
    var torrentOnly = 0
    val stremio = async {
        try {
            val outcome = fetchLocalSources(profile, prepared, ::append)
            torrentOnly += outcome.torrentOnly
        }
        catch (_: TimeoutCancellationException) { stremioFailed = true }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { stremioFailed = true }
    }
    val integrations = async {
        try { append(fetchIntegrationSources(profile, prepared, ::append)) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { integrationFailed = true }
    }
    stremio.await(); integrations.await()
    if (found.isEmpty() && (stremioFailed || integrationFailed))
        throw LocalFailure("Could not load sources. Check the configured providers and try again.")
    SourcesOutcome(found.values.toList(), if (found.isEmpty()) torrentOnly else 0)
}
