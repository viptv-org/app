package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS = 45_000L

internal data class PreparedNextSource(val source: SourceCandidate, val sources: List<SourceCandidate>, val groups: SourceGroups)

internal fun interface NextSourcePreparer {
    suspend fun prepare(remote: RemoteConnection?, local: LocalConnection?, profile: String, card: Card): PreparedNextSource?

    companion object {
        val Device = NextSourcePreparer { remote, local, profile, card ->
            val deviceHeight = deviceMaxVideoHeight()
            val ranked = coroutineScope {
                val ranking = ProgressiveSourceRanking(this,
                    rank = { rows -> local?.rankSources(profile, rows, deviceHeight) ?: requireNotNull(remote).rankSources(profile, rows, deviceHeight) },
                    show = {}, onMatch = {})
                val outcome = local?.sources(profile, card, ranking::arrived) ?: SourcesOutcome(requireNotNull(remote).sources(card, ranking::arrived))
                ranking.arrived(outcome.candidates)
                ranking.finish()
            }
            val matched = ranked?.takeIf { it.bestMatchFullyMatches } ?: return@NextSourcePreparer null
            val target = matched.autoplayTarget() ?: return@NextSourcePreparer null
            PreparedNextSource(target, matched.rows, matched.groups)
        }
    }
}

internal fun ApplicationController.maintainNextEpisodePreparation(positionMillis: Long, durationMillis: Long?) {
    val state = mutable.value
    val target = state.nextEpisodePreparationTarget()
    if (target == null) { cancelNextEpisodePreparation(); return }
    val key = preparationKeyFor(target, state)
    val prepared = state.nextEpisodePreparation
    val expired = prepared != null && prepared.status == NextEpisodePreparationStatus.Ready &&
        clockMillis() - (prepared.readyAtMillis ?: prepared.startedAtMillis) >= PREPARED_NEXT_EPISODE_TTL_MILLIS
    if (key == nextEpisodePreparationKey && !expired) return
    cancelNextEpisodePreparation()
    if (nextEpisodePreparationDue(positionMillis, durationMillis)) startNextEpisodePreparation(target, key)
}

private fun ApplicationController.startNextEpisodePreparation(card: Card, key: NextEpisodePreparationKey) {
    val generation = ++nextEpisodePreparationGeneration
    nextEpisodePreparationKey = key
    val startedAt = clockMillis()
    updateState { it.copy(nextEpisodePreparation = NextEpisodePreparation(card, key.profile, NextEpisodePreparationStatus.Preparing, startedAtMillis = startedAt)) }
    val remote = connection
    val device = local
    nextEpisodePreparationJob = scope.launch {
        val prepared = try { withTimeoutOrNull(NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS) { nextSourcePreparer.prepare(remote, device, key.profile, card) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (generation != nextEpisodePreparationGeneration) return@launch
        val readyAt = clockMillis()
        updateState { latest ->
            val current = latest.nextEpisodePreparation?.takeIf { cardLookupKey(it.card) == cardLookupKey(card) } ?: return@updateState latest
            latest.copy(nextEpisodePreparation = current.copy(source = prepared?.source,
                sources = prepared?.sources.orEmpty(), groups = prepared?.groups ?: SourceGroups(),
                status = if (prepared?.source != null) NextEpisodePreparationStatus.Ready else NextEpisodePreparationStatus.Unavailable,
                readyAtMillis = readyAt))
        }
    }
}

internal fun ApplicationController.cancelNextEpisodePreparation() {
    nextEpisodePreparationGeneration++
    nextEpisodePreparationJob?.cancel(); nextEpisodePreparationJob = null
    nextEpisodePreparationKey = null
    if (mutable.value.nextEpisodePreparation != null) updateState { it.copy(nextEpisodePreparation = null) }
}

fun ApplicationController.playNextEpisode(card: Card) {
    val state = mutable.value
    val prepared = state.nextEpisodePreparation?.takeIf {
        it.status == NextEpisodePreparationStatus.Ready && it.source != null &&
            it.profile == state.selectedProfile && cardLookupKey(it.card) == cardLookupKey(card) &&
            preparationKeyFor(it.card, state) == nextEpisodePreparationKey
    }
    if (prepared == null) { closePlayer(); chooseSource(card); return }
    startPreparedNextEpisode(prepared)
}

private fun ApplicationController.startPreparedNextEpisode(prepared: NextEpisodePreparation) {
    val source = prepared.source ?: return
    cancelNextEpisodePreparation()
    if (!completionSeen) saveProgress(false, final = true)
    updateState { it.copy(sourceTitle = prepared.card, sources = prepared.sources, sourceGroups = prepared.groups,
        torrentOnlySources = 0, playTapTitle = null, detailFailure = null, detailRetryable = true, error = null, errorCode = null) }
    playSource(source)
}