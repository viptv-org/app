package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal const val NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS = 45_000L
internal const val NEXT_EPISODE_PROMOTE_TIMEOUT_MILLIS = 60_000L
private const val NEXT_EPISODE_HEARTBEAT_RETRY_MILLIS = 60_000L
private const val NEXT_EPISODE_HEARTBEAT_MAX_RETRY_MILLIS = 15_000L

internal data class PreparedNextSource(val source: SourceCandidate, val ranked: RankedSources, val context: RankingContext? = null)

internal fun interface NextEpisodeDeliveryPreparer {
    suspend fun prepare(remote: RemoteConnection?, source: SourceCandidate, profile: String): PreparedPlaybackLease?

    companion object {
        val SourceOnly = NextEpisodeDeliveryPreparer { _, _, _ -> null }
    }
}

internal fun interface NextSourcePreparer {
    suspend fun prepare(remote: RemoteConnection?, local: LocalConnection?, profile: String, card: Card,
        remembered: RememberedSource?, context: RankingContext): PreparedNextSource?

    companion object {
        val Device = NextSourcePreparer { remote, local, profile, card, remembered, context ->
            val ranked = coroutineScope {
                val ranking = ProgressiveSourceRanking(this,
                    rank = { rows -> local?.rankSources(profile, rows, context) ?: requireNotNull(remote).rankSources(profile, rows, context) },
                    show = {})
                val observed: (SourcesOutcome) -> Unit = { ranking.arrived(it.candidates) }
                val outcome = local?.discoverSources(profile, card, context, observed) ?: requireNotNull(remote).sources(card, context.reuse, context.device, publish = observed)
                ranking.arrived(outcome.candidates)
                ranking.finish()
            }
            val matched = ranked ?: return@NextSourcePreparer null
            // A remembered choice decides alone: a row in another audio never stands in for it, so Next asks like the unprepared path does.
            val target = if (remembered != null) {
                if (matched.reuseSupported) matched.reuseIds.firstOrNull()?.let { id -> matched.rows.find { it.id == id } }
                else if (local != null) matched.matchingSource(remembered) else null
            }
                else matched.takeIf { it.bestMatchFullyMatches }?.autoplayTarget()
            target?.let { PreparedNextSource(it, matched, context) }
        }
    }
}

internal fun ApplicationController.maintainNextEpisodePreparation(positionMillis: Long, durationMillis: Long?) {
    val state = mutable.value
    val target = state.nextEpisodePreparationTarget()
    if (target == null) { cancelNextEpisodePreparation(); return }
    val key = preparationKeyFor(target, state)
    if (key == nextEpisodePreparationKey && state.nextEpisodePreparation?.preparedAtMillis?.let { clockMillis() - it >= NEXT_PREPARED_SOURCE_TTL_MILLIS } != true) return
    cancelNextEpisodePreparation()
    if (nextEpisodePreparationDue(positionMillis, durationMillis)) startNextEpisodePreparation(target, key)
}

private fun ApplicationController.startNextEpisodePreparation(card: Card, key: NextEpisodePreparationKey) {
    val generation = ++nextEpisodePreparationGeneration
    nextEpisodePreparationKey = key
    updateState { it.copy(nextEpisodePreparation = NextEpisodePreparation(card, key.profile, NextEpisodePreparationStatus.Preparing)) }
    val remote = connection
    val device = local
    nextEpisodePreparationJob = scope.launch {
        val remembered = if (mutable.value.preferences.rememberedSourceReuseEnabled())
            sourceMemory.choice(key.profile, card) else null
        val context = rankingContext(seriesMemoryOf(key.profile, card), capability).copy(providerAffinity = sourceMemory.affinity(key.profile),
            reuse = if (remembered != null) sourceMemory.context(key.profile, card) else SourceReuseContext())
        val prepared = try { withTimeoutOrNull(NEXT_EPISODE_PREPARE_TIMEOUT_MILLIS) {
            nextSourcePreparer.prepare(remote, device, key.profile, card, remembered, context) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
        if (generation != nextEpisodePreparationGeneration) return@launch
        val lease = prepared?.source?.let { source -> nextEpisodeDeliveryPreparer.prepare(remote, source, key.profile) }
        if (generation != nextEpisodePreparationGeneration) { lease?.let { releaseQuietly(it) }; return@launch }
        nextEpisodePreparedLease = lease
        updateState { latest ->
            val current = latest.nextEpisodePreparation?.takeIf { cardLookupKey(it.card) == cardLookupKey(card) } ?: return@updateState latest
            latest.copy(nextEpisodePreparation = current.copy(source = prepared?.source, ranked = prepared?.ranked,
                status = if (prepared?.source != null) NextEpisodePreparationStatus.Ready else NextEpisodePreparationStatus.Unavailable,
                preparedAtMillis = prepared?.source?.let { clockMillis() }, rankingContext = prepared?.context))
        }
        if (lease != null) keepPreparedPlaybackLease(lease)
    }
}

private suspend fun ApplicationController.releaseQuietly(lease: PreparedPlaybackLease) {
    try { lease.release() } catch (_: Exception) { }
}

internal fun ApplicationController.keepPreparedPlaybackLease(lease: PreparedPlaybackLease) {
    nextEpisodePreparationRenewal?.cancel()
    nextEpisodePreparationRenewal = scope.launch {
        val interval = lease.renewSeconds.coerceIn(1, 30) * 1000
        while (true) {
            delay(interval)
            if (nextEpisodePreparedLease !== lease) return@launch
            if (!renewPreparedQuietly(lease)) {
                if (nextEpisodePreparedLease === lease) nextEpisodePreparedLease = null
                return@launch
            }
        }
    }
}

private suspend fun ApplicationController.renewPreparedQuietly(lease: PreparedPlaybackLease): Boolean {
    var wait = 1_000L
    var spent = 0L
    while (true) {
        val failure = try { lease.renew(); return true }
            catch (error: CancellationException) { if (error is TimeoutCancellationException) error else throw error }
            catch (error: Exception) { error }
        if (!failure.transientFailure() || spent + wait > NEXT_EPISODE_HEARTBEAT_RETRY_MILLIS) return false
        delay(wait); spent += wait; wait = (wait * 2).coerceAtMost(NEXT_EPISODE_HEARTBEAT_MAX_RETRY_MILLIS)
    }
}

private fun ApplicationController.clearNextEpisodePreparation(releaseLease: Boolean): Job? {
    nextEpisodePreparationGeneration++
    nextEpisodePreparationJob?.cancel(); nextEpisodePreparationJob = null
    nextEpisodePreparationKey = null
    nextEpisodePreparationRenewal?.cancel(); nextEpisodePreparationRenewal = null
    val lease = nextEpisodePreparedLease
    nextEpisodePreparedLease = null
    if (mutable.value.nextEpisodePreparation != null) updateState { it.copy(nextEpisodePreparation = null) }
    return if (releaseLease && lease != null) scope.launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { releaseQuietly(lease) } } else null
}

internal fun ApplicationController.cancelNextEpisodePreparation(): Job? = clearNextEpisodePreparation(releaseLease = true)

internal fun ApplicationController.startPreparedNextEpisode(card: Card): Boolean {
    val state = mutable.value
    if (!state.preferences.rememberedSourceReuseEnabled()) return false
    val prepared = state.nextEpisodePreparation?.takeIf {
        it.status == NextEpisodePreparationStatus.Ready && it.source != null &&
            (it.preparedAtMillis == null || clockMillis() - it.preparedAtMillis < NEXT_PREPARED_SOURCE_TTL_MILLIS) &&
            it.profile == state.selectedProfile && cardLookupKey(it.card) == cardLookupKey(card) &&
            preparationKeyFor(it.card, state) == nextEpisodePreparationKey
    } ?: return false
    handOffToPreparedNextEpisode(prepared)
    return true
}

private fun ApplicationController.handOffToPreparedNextEpisode(prepared: NextEpisodePreparation) {
    val source = prepared.source ?: return
    val ranked = prepared.ranked ?: return
    val lease = nextEpisodePreparedLease
    clearNextEpisodePreparation(releaseLease = false)
    if (!completionSeen) saveProgress(false, final = true)
    updateState { it.copy(sourceTitle = prepared.card, sources = ranked.rows, sourceGroups = ranked.groups,
        torrentOnlySources = 0, playTapTitle = null, detailFailure = null, detailRetryable = true, error = null, errorCode = null) }
    if (lease != null) promotePreparedNextEpisode(source, ranked, lease, prepared.card)
    else startBestMatch(ranked, source, prepared.rankingContext ?: stateContextFor(prepared.card))
}

private fun ApplicationController.promotePreparedNextEpisode(source: SourceCandidate, ranked: RankedSources, lease: PreparedPlaybackLease, card: Card) {
    updateState { it.copy(bestMatch = BestMatchSession(ranked.rows.filter { !ranked.reuseSupported || it.id in ranked.reuseIds }, tried = listOf(source.id), playingId = source.id, reuseContext = stateContextFor(card)?.takeIf { ranked.reuseSupported }, reuseAudioLanguages = ranked.reuseAudioLanguages),
        fallbackNotice = null, audioNotice = null, playingSource = source, openingSource = source) }
    run { promotePreparedDelivery(source, ranked, lease, card) }
}

private suspend fun ApplicationController.promotePreparedDelivery(source: SourceCandidate, ranked: RankedSources, lease: PreparedPlaybackLease, card: Card) {
    progressJob?.join()
    val outgoingId = remotePlayback?.input?.id
    val (outgoing, settled) = detachRemotePlaybackForPromotion()
    updateState { next -> next.copy(playback = null, playbackSegments = emptyList(), presentation = null, findingSources = false) }
    settled?.join()
    val profile = requireNotNull(mutable.value.selectedProfile)
    val promoted = try {
        withTimeoutOrNull(NEXT_EPISODE_PROMOTE_TIMEOUT_MILLIS) { lease.promote(outgoingId) }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { null }
    closePlaybackQuietly(outgoing)
    if (promoted == null) {
        releaseQuietly(lease)
        fallbackPreparedHandoff(source, ranked)
        return
    }
    if (mutable.value.selectedProfile != profile || mutable.value.sourceTitle?.let(::cardLookupKey) != cardLookupKey(card)) {
        closePlaybackQuietly(promoted)
        return
    }
    adoptPromotedPlayback(promoted, source, card, profile)
}

private suspend fun ApplicationController.fallbackPreparedHandoff(source: SourceCandidate, ranked: RankedSources) {
    updateState { it.copy(bestMatch = BestMatchSession(ranked.rows.filter { !ranked.reuseSupported || it.id in ranked.reuseIds }, tried = listOf(source.id), playingId = source.id, reuseContext = mutable.value.sourceTitle?.let(::stateContextFor)?.takeIf { ranked.reuseSupported }, reuseAudioLanguages = ranked.reuseAudioLanguages),
        fallbackNotice = null, audioNotice = null, playingSource = source, openingSource = source) }
    openSourceNow(source, forceGateway = false, positionMillis = null, pausedAfterOpen = false, choice = SourceChoice.None)
}

private fun ApplicationController.stateContextFor(card: Card): RankingContext? {
    val state = mutable.value
    val profile = state.selectedProfile ?: return null
    if (!state.preferences.rememberedSourceReuseEnabled()) return null
    val reuse = sourceMemory.context(profile, card)
    return RankingContext(reuse = reuse).takeIf { reuse.present }
}
