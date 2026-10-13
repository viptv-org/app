package org.viptv.app.connection

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal suspend fun NativeLocalConnection.fetchMergedLocalSources(profile: String, card: Card,
    publish: (List<SourceCandidate>) -> Unit): List<SourceCandidate> = coroutineScope {
    val gate = Mutex()
    val found = linkedMapOf<String, SourceCandidate>()
    suspend fun append(cards: List<SourceCandidate>) = gate.withLock {
        cards.forEach { found[it.id] = it }
        publish(found.values.toList())
    }
    var stremioFailed = false
    var integrationFailed = false
    val stremio = async {
        try { append(withTimeout(30_000) { fetchLocalSources(profile, card) }) }
        catch (_: TimeoutCancellationException) { stremioFailed = true }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { stremioFailed = true }
    }
    val integrations = async {
        try { append(fetchIntegrationSources(profile, card, ::append)) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { integrationFailed = true }
    }
    stremio.await(); integrations.await()
    if (found.isEmpty() && (stremioFailed || integrationFailed))
        throw LocalFailure("Could not load sources. Check the configured providers and try again.")
    found.values.toList()
}
