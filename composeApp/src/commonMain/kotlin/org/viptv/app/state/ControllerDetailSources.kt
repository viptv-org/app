package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.viptv.app.connection.LocalFailure
import org.viptv.app.connection.SourceGroups
import org.viptv.app.connection.SourceProducerOutcome
import org.viptv.app.connection.SourcesOutcome
import org.viptv.app.connection.rankSources
import org.viptv.app.connection.rankingContext
import org.viptv.app.connection.sources
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val DETAIL_SOURCE_PREVIEW_DELAY_MILLIS = 400L
internal const val DETAIL_SOURCE_PREVIEW_TIMEOUT_MILLIS = 180_000L

fun ApplicationController.previewDetailSources(card: Card) {
    val profile = mutable.value.selectedProfile ?: return
    val target = resolveSourcePreviewTarget(card) ?: return
    val key = detailSourcePreviewKey(target, profile)
    if (sourcePreviewSession?.key == key) return
    cancelDetailSourcePreview()
    val session = DetailSourcePreviewSession(key)
    sourcePreviewSession = session
    updateState { it.copy(detailSourcePreview = DetailSourcePreview()) }
    session.job = scope.launch {
        try {
            delay(DETAIL_SOURCE_PREVIEW_DELAY_MILLIS)
            discoverDetailSources(session, target, profile)
        } catch (error: CancellationException) {
            throw error
        }
    }
}

fun ApplicationController.cancelDetailSourcePreview() {
    val session = sourcePreviewSession ?: return
    sourcePreviewSession = null
    session.adopted = false
    session.job?.cancel()
    updateState { it.copy(detailSourcePreview = null) }
}

internal fun ApplicationController.adoptDetailSourcePreview(card: Card): DetailSourcePreview? {
    val session = sourcePreviewSession ?: return null
    val profile = mutable.value.selectedProfile ?: return null
    if (session.key != detailSourcePreviewKey(card, profile)) return null
    val preview = mutable.value.detailSourcePreview ?: return null
    if (preview.rows.isEmpty() && preview.settled) return null
    session.adopted = true
    return preview
}

private fun ApplicationController.resolveSourcePreviewTarget(card: Card): Card? {
    if (card.identity.episode != null || card.identity.kind != "series") return card
    val state = mutable.value
    if (state.detailShell) return null
    val detail = state.detail?.takeIf { it.item.identity == card.identity && it.item.provider_id == card.provider_id &&
        it.item.metadata_origin == card.metadata_origin } ?: return null
    return try { sourceTarget(card, detail) } catch (_: LocalFailure) { null }
}

private suspend fun ApplicationController.discoverDetailSources(session: DetailSourcePreviewSession, card: Card, profile: String) {
    val device = local
    val remote = connection
    if (device == null && remote == null) {
        publishDetailSourcePreview(session, DetailSourcePreview(settled = true, failed = true))
        return
    }
    val context = rankingContext(seriesMemoryOf(profile, card), sourceMemory.affinity(profile), capability)
    var rows: List<SourceCandidate> = emptyList()
    var groups = SourceGroups()
    var producers: List<SourceProducerOutcome> = emptyList()
    var torrentOnly = 0
    fun publish(settled: Boolean, failed: Boolean = false) =
        publishDetailSourcePreview(session, DetailSourcePreview(rows, groups, producers, torrentOnly, settled, failed))
    val finished = try {
        withTimeoutOrNull(DETAIL_SOURCE_PREVIEW_TIMEOUT_MILLIS) {
            coroutineScope {
                val ranking = ProgressiveSourceRanking(this,
                    rank = { candidates -> device?.rankSources(profile, candidates, context) ?: remote!!.rankSources(profile, candidates, context) },
                    show = { ranked -> rows = ranked.rows; groups = ranked.groups; publish(false) })
                val observed: (SourcesOutcome) -> Unit = { outcome ->
                    producers = producersLabelled(outcome.producers)
                    torrentOnly = outcome.torrentOnlyProviders
                    publish(false)
                    ranking.arrived(outcome.candidates)
                }
                val outcome = device?.sources(profile, card, observed) ?: remote!!.sources(card, observed)
                producers = producersLabelled(outcome.producers)
                torrentOnly = outcome.torrentOnlyProviders
                ranking.arrived(outcome.candidates)
                ranking.finish().also { ranked -> ranked?.let { rows = it.rows; groups = it.groups } }
            }
            true
        } ?: false
    } catch (_: TimeoutCancellationException) {
        false
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
    publish(settled = true, failed = !finished && rows.isEmpty())
}

private fun ApplicationController.publishDetailSourcePreview(session: DetailSourcePreviewSession, preview: DetailSourcePreview) {
    if (sourcePreviewSession !== session) return
    if (!session.adopted) {
        updateState { it.copy(detailSourcePreview = preview) }
        return
    }
    val unavailable = preview.settled && preview.failed && preview.rows.isEmpty()
    updateState {
        it.copy(detailSourcePreview = preview, sources = preview.rows, sourceGroups = preview.groups,
            sourceProducers = preview.producers, torrentOnlySources = preview.torrentOnly, findingSources = !preview.settled,
            error = if (unavailable) SOURCES_UNAVAILABLE_LABEL else it.error)
    }
}

