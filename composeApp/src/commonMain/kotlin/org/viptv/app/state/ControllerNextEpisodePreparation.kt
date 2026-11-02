package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS = 45_000L

internal data class PreparedNextSource(val source: SourceCandidate, val ranked: RankedSources)

internal fun interface NextSourcePreparer {
    suspend fun prepare(remote: RemoteConnection?, local: LocalConnection?, profile: String, card: Card,
        remembered: RememberedSource?, context: RankingContext): PreparedNextSource?

    companion object {
        val Device = NextSourcePreparer { remote, local, profile, card, remembered, context ->
            val ranked = coroutineScope {
                val ranking = ProgressiveSourceRanking(this,
                    rank = { rows -> local?.rankSources(profile, rows, context) ?: requireNotNull(remote).rankSources(profile, rows, context) },
                    show = {}, onMatch = {})
                val observed: (SourcesOutcome) -> Unit = { ranking.arrived(it.candidates) }
                val outcome = local?.sources(profile, card, observed) ?: requireNotNull(remote).sources(card, observed)
                ranking.arrived(outcome.candidates)
                ranking.finish()
            }
            val matched = ranked ?: return@NextSourcePreparer null
            // A remembered choice decides alone: a row in another audio never stands in for it, so Next asks like the unprepared path does.
            val target = if (remembered != null) matched.matchingSource(remembered)
                else matched.takeIf { it.bestMatchFullyMatches }?.autoplayTarget()
            target?.let { PreparedNextSource(it, matched) }
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
        val remembered = if (mutable.value.preferences.rememberedSourceReuseEnabled())
            sourceMemory.entry(key.profile, card) else null
        val context = rankingContext(seriesMemoryOf(key.profile, card))
        val prepared = try { withTimeoutOrNull(NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS) {
            nextSourcePreparer.prepare(remote, device, key.profile, card, remembered, context) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (generation != nextEpisodePreparationGeneration) return@launch
        val readyAt = clockMillis()
        updateState { latest ->
            val current = latest.nextEpisodePreparation?.takeIf { cardLookupKey(it.card) == cardLookupKey(card) } ?: return@updateState latest
            latest.copy(nextEpisodePreparation = current.copy(source = prepared?.source, ranked = prepared?.ranked,
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

internal fun ApplicationController.startPreparedNextEpisode(card: Card): Boolean {
    val state = mutable.value
    if (!state.preferences.rememberedSourceReuseEnabled()) return false
    val prepared = state.nextEpisodePreparation?.takeIf {
        it.status == NextEpisodePreparationStatus.Ready && it.source != null &&
            it.profile == state.selectedProfile && cardLookupKey(it.card) == cardLookupKey(card) &&
            preparationKeyFor(it.card, state) == nextEpisodePreparationKey
    } ?: return false
    handOffToPreparedNextEpisode(prepared)
    return true
}

private fun ApplicationController.handOffToPreparedNextEpisode(prepared: NextEpisodePreparation) {
    val source = prepared.source ?: return
    val ranked = prepared.ranked ?: return
    cancelNextEpisodePreparation()
    if (!completionSeen) saveProgress(false, final = true)
    updateState { it.copy(sourceTitle = prepared.card, sources = ranked.rows, sourceGroups = ranked.groups,
        torrentOnlySources = 0, playTapTitle = null, detailFailure = null, detailRetryable = true, error = null, errorCode = null) }
    startBestMatch(ranked, source)
}