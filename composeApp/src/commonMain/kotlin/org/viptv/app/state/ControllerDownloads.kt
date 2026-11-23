package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import org.viptv.app.connection.*
import org.viptv.app.downloads.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

private const val DOWNLOAD_PLAYBACK_PREFIX = "download:"
private const val WATCHED_FRACTION = 0.95
private const val EPISODE_PAGE_LIMIT = 128
private const val EPISODE_PAGE_COUNT = 40

val ApplicationController.downloads: Downloads? get() = downloadEngine

internal fun ApplicationController.startDownloads() {
    val engine = downloadEngine ?: return
    engine.resolver = RemoteDownloadResolver(this)
    engine.subtitleFetcher = RemoteDownloadSubtitles(this)
    scope.launch {
        mutable.collect { state ->
            val items = engine.items.value
            val offline = engine.network.value == DownloadNetwork.None
            if (state.downloads != items || state.offline != offline) updateState { it.copy(downloads = items, offline = offline) }
            val downloaded = downloadedSourcesFor(engine, state)
            val downloadable = if (state.sourcesForDownload) state.sources.associate { it.id to downloadBlockReason(it) } else emptyMap()
            if (state.downloadedSources != downloaded || state.downloadableSources != downloadable)
                updateState { it.copy(downloadedSources = downloadedSourcesFor(engine, it),
                    downloadableSources = if (it.sourcesForDownload) it.sources.associate { row -> row.id to downloadBlockReason(row) } else emptyMap()) }
        }
    }
    scope.launch { engine.items.collect { items -> updateState { it.copy(downloads = items) } } }
    scope.launch {
        engine.network.collect { network ->
            updateState { it.copy(offline = network == DownloadNetwork.None) }
            if (network != DownloadNetwork.None) {
                syncOfflineProgress()
                if (offlineStart && connection == null && mutable.value.screen == Screen.Downloads) restore(startupDatabase)
            }
        }
    }
    scope.launch {
        mutable.map { it.selectedProfile }.distinctUntilChanged().collect {
            engine.wakeUp()
            syncOfflineProgress()
        }
    }
}

internal fun ApplicationController.offlineAtStart(): Boolean {
    val engine = downloadEngine ?: return false
    return engine.network.value == DownloadNetwork.None && engine.records().isNotEmpty()
}

fun ApplicationController.downloadTitle(card: Card, source: SourceCandidate? = null) {
    val profile = mutable.value.selectedProfile ?: return
    enqueueDownloads(listOf(DownloadRequest(profile, card, seriesTitleFor(card), source = source)))
}

fun ApplicationController.chooseDownloadSource(card: Card) = chooseSource(card, startPlayback = false, forDownload = true)

fun ApplicationController.changeDownloadSource(id: String) {
    val record = downloadEngine?.record(id) ?: return
    chooseDownloadSource(record.card)
}

fun ApplicationController.downloadSeason(series: Card, season: Long, source: SourceCandidate? = null) =
    downloadEpisodes(series, source) { episodes -> episodes.filter { it.identity.episode?.season == season } }

fun ApplicationController.downloadNext(series: Card, count: Int, source: SourceCandidate? = null) =
    downloadEpisodes(series, source) { episodes ->
        val regular = episodes.filter { (it.identity.episode?.season ?: 1L) != 0L }
        val lastWatched = regular.indexOfLast { (it.progress ?: 0.0) >= WATCHED_FRACTION }
        regular.drop(lastWatched + 1).take(count)
    }

fun ApplicationController.downloadSeries(series: Card, source: SourceCandidate? = null) =
    downloadEpisodes(series, source) { episodes -> episodes.filter { (it.identity.episode?.season ?: 1L) != 0L } }

private fun downloadedSourcesFor(engine: DownloadEngine, state: ApplicationState): Map<String, String> {
    val title = state.sourceTitle ?: return emptyMap()
    val profile = state.selectedProfile ?: return emptyMap()
    if (state.sources.isEmpty()) return emptyMap()
    return downloadedSourceIds(listOfNotNull(engine.record(downloadId(profile, title))), state.sources)
}

internal fun ApplicationController.completedDownloadFor(card: Card): String? {
    val engine = downloadEngine ?: return null
    val profile = mutable.value.selectedProfile ?: return null
    return engine.record(downloadId(profile, card))?.takeIf { it.state == DownloadState.Completed }?.id
}

internal fun ApplicationController.pickDownloadSource(source: SourceCandidate) {
    val engine = downloadEngine ?: return
    val state = mutable.value
    val card = state.sourceTitle ?: return
    val profile = state.selectedProfile ?: return
    downloadBlockReason(source)?.let { reason -> updateState { it.copy(notice = AppNotice(reason)) }; return }
    if (source.id in state.downloadedSources) return
    val existing = engine.record(downloadId(profile, card))
    if (existing != null) engine.replaceSource(existing.id, source) else downloadTitle(card, source)
    backSources()
}

internal fun ApplicationController.playLocalCopy(source: SourceCandidate): Boolean {
    val id = mutable.value.downloadedSources[source.id] ?: return false
    if (downloadEngine?.record(id)?.state != DownloadState.Completed) return false
    playDownload(id)
    return true
}

fun ApplicationController.playDownload(id: String) = playDownload(id, startOver = false)

internal fun ApplicationController.playDownload(id: String, startOver: Boolean) {
    val engine = downloadEngine ?: return
    val record = engine.record(id)?.takeIf { it.state == DownloadState.Completed } ?: return
    val path = engine.localFile(id) ?: return
    val returnTo = mutable.value.screen.takeIf { it != Screen.Player } ?: Screen.Downloads
    cancelSeekReopen()
    job?.cancel()
    clearBestMatchSession()
    if (mutable.value.playback != null && !completionSeen) saveProgress(false, final = true)
    releasePlaybackSession()
    val preferences = mutable.value.preferences?.preferences ?: ProfilePreferences()
    val resume = record.positionMillis.takeIf { !startOver && record.watchedAtMillis == null && it > 0 } ?: 0L
    val input = PlaybackInput("$DOWNLOAD_PLAYBACK_PREFIX$id", "file://$path", positionMillis = resume, profileId = record.profileId,
        durationMillis = record.durationMillis, preferences = preferences, diagnostics = playbackDiagnostics,
        mimeType = record.mimeType, title = record.card.title, startChosen = true,
        subtitleContent = { subtitle -> engine.subtitleBytes(id, subtitle) ?: ByteArray(0) })
    input.updateProviderSubtitles(record.subtitles.map { it.candidate() })
    downloadReturn = returnTo
    downloadWatchNoted = null
    tracking = null
    firstFrameSeen = false
    lastProgress = null
    completionSeen = false
    progressSavedAt = freshness.time.markNow()
    engine.playing(id)
    updateState { it.copy(playback = input, sourceTitle = record.card, playingSource = record.source, paused = false, presentation = null,
        screen = Screen.Player, error = null, playbackFailure = null, openingSource = null, pendingAction = null, navForward = emptyList(),
        detailReturnScreen = returnTo) }
}

internal fun PlaybackInput.downloadId(): String? = id.takeIf { it.startsWith(DOWNLOAD_PLAYBACK_PREFIX) }?.removePrefix(DOWNLOAD_PLAYBACK_PREFIX)

internal fun ApplicationController.endDownloadPlayback(input: PlaybackInput?) {
    val engine = downloadEngine ?: return
    val id = input?.downloadId() ?: return
    scope.launch { if (engine.playingId == id) engine.playing(null) }
}

internal fun ApplicationController.storeDownloadSubtitle(input: PlaybackInput, card: Card, subtitleId: String, content: ByteArray) {
    val engine = downloadEngine ?: return
    val profile = input.profileId ?: return
    val record = engine.record(downloadId(profile, card)) ?: return
    val subtitle = input.providerSubtitles.value.firstOrNull { it.id == subtitleId } ?: return
    if (content.isNotEmpty()) engine.storeSubtitle(record.id, subtitle, content)
}

internal fun ApplicationController.closeDownloadScreen(): Screen? {
    val input = mutable.value.playback ?: return null
    input.downloadId() ?: return null
    return (downloadReturn ?: Screen.Downloads).also { downloadReturn = null }
}

internal fun ApplicationController.saveDownloadProgress(input: PlaybackInput, completed: Boolean): Job? {
    val id = input.downloadId() ?: return null
    val engine = downloadEngine ?: return null
    val record = engine.record(id) ?: return null
    val snapshot = lastProgress ?: return null
    val duration = snapshot.durationMillis
    val position = if (completed && duration != null) duration else snapshot.positionMillis
    if (position <= 0) return null
    val watched = completed || (duration != null && duration > 0 && position >= duration * WATCHED_FRACTION)
    engine.played(id, position, duration, watched)
    engine.queueProgress(OfflineProgress(record.profileId, record.card, position, duration, ended = watched, atMillis = clockMillis()))
    progressSavedAt = freshness.time.markNow()
    if (watched) noteWatched(input, record.profileId, record.card)
    return scope.launch { syncOfflineProgress() }.also { progressJob = it }
}

internal fun ApplicationController.noteStreamedProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?, completed: Boolean) {
    val engine = downloadEngine ?: return
    val profile = input.profileId ?: return
    val watched = completed || (durationMillis != null && durationMillis > 0 && positionMillis >= durationMillis * WATCHED_FRACTION)
    if (!watched) return
    engine.record(downloadId(profile, card))?.let { engine.played(it.id, positionMillis, durationMillis, watched = true) }
    noteWatched(input, profile, card)
}

private fun ApplicationController.noteWatched(input: PlaybackInput, profile: String, card: Card) {
    if (downloadWatchNoted === input) return
    downloadWatchNoted = input
    val engine = downloadEngine ?: return
    val keep = engine.settings.value.keepNextEpisodes
    if (keep <= 0 || card.identity.episode == null || card.parent_identity == null) return
    val series = seriesCardFor(card)
    scope.launch {
        val episodes = try { seriesEpisodes(series) } catch (error: CancellationException) { throw error } catch (_: Exception) { return@launch }
        if (mutable.value.selectedProfile != profile) return@launch
        val index = episodes.indexOfFirst { cardLookupKey(it) == cardLookupKey(card) }
        if (index < 0) return@launch
        val next = episodes.drop(index + 1).filter { (it.identity.episode?.season ?: 1L) != 0L }.take(keep)
        engine.enqueue(next.map { DownloadRequest(profile, it, series.title, smart = true) })
    }
}

internal suspend fun ApplicationController.syncOfflineProgress() {
    val engine = downloadEngine ?: return
    if (engine.network.value == DownloadNetwork.None) return
    offlineProgressGate.withLock {
        val remote = connection ?: return
        val profile = mutable.value.selectedProfile ?: return
        for (pending in engine.pendingProgress().filter { it.profileId == profile }) {
            val sent = try {
                remote.recordProgress(PlaybackInput("offline", "", profileId = pending.profileId), pending.card,
                    pending.positionMillis, pending.durationMillis, ended = pending.ended)
                true
            } catch (error: CancellationException) { throw error }
            catch (error: RemoteFailure) { !error.transientFailure() }
            catch (_: Exception) { false }
            if (!sent) return
            engine.settleProgress(pending)
        }
    }
}

private fun ApplicationController.enqueueDownloads(requests: List<DownloadRequest>) {
    val engine = downloadEngine ?: return
    if (requests.isEmpty()) return
    engine.enqueue(requests)
    updateState { it.copy(downloads = engine.items.value) }
}

private fun ApplicationController.downloadEpisodes(series: Card, release: SourceCandidate?, choose: (List<Card>) -> List<Card>) {
    val engine = downloadEngine ?: return
    val profile = mutable.value.selectedProfile ?: return
    val detail = mutable.value.detail?.takeIf { cardLookupKey(it.item) == cardLookupKey(series) }
    val known = detail?.let { choose(it.episodes) }.orEmpty()
    if (known.isNotEmpty()) enqueueDownloads(known.map { DownloadRequest(profile, it, series.title, release = release) })
    scope.launch {
        val episodes = try { seriesEpisodes(series) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (known.isEmpty()) updateState { it.copy(notice = AppNotice(failureMessage(error) ?: "Could not load the episodes to download.")) }
            return@launch
        }
        if (mutable.value.selectedProfile != profile) return@launch
        engine.enqueue(choose(episodes).map { DownloadRequest(profile, it, series.title, release = release) })
        updateState { it.copy(downloads = engine.items.value) }
    }
}

private suspend fun ApplicationController.seriesEpisodes(series: Card): List<Card> {
    val remote = connection
    val device = local
    val profile = requireNotNull(mutable.value.selectedProfile)
    val episodes = mutableListOf<Card>()
    var offset = 0
    repeat(EPISODE_PAGE_COUNT) {
        val page = device?.detailPage(profile, series, offset, EPISODE_PAGE_LIMIT)
            ?: requireNotNull(remote).detail(series, offset, EPISODE_PAGE_LIMIT)
        val total = page.seasons.sumOf { it.episode_count }
        episodes += page.episodes
        offset += page.episodes.size
        if (page.episodes.isEmpty() || offset >= total) return episodes.distinctBy(::cardLookupKey)
    }
    return episodes.distinctBy(::cardLookupKey)
}

private fun ApplicationController.seriesTitleFor(card: Card): String? {
    if (card.identity.episode == null) return null
    return mutable.value.detail?.item?.takeIf { item -> card.parent_identity?.let { sameLookupIdentity(item.identity, it) } == true }?.title
}

private fun ApplicationController.seriesCardFor(card: Card): Card {
    val parent = requireNotNull(card.parent_identity)
    mutable.value.detail?.item?.takeIf { sameLookupIdentity(it.identity, parent) }?.let { return it }
    val title = downloadEngine?.record(downloadId(mutable.value.selectedProfile.orEmpty(), card))?.seriesTitle ?: card.title
    return Card(parent, provider_id = card.provider_id, title = title, badges = emptyList(), metadata_origin = card.metadata_origin)
}

internal class RemoteDownloadResolver(private val controller: ApplicationController) : DownloadResolver {
    override fun accepts(profileId: String): Boolean =
        controller.connection != null && controller.mutable.value.selectedProfile == profileId

    override suspend fun resolve(record: DownloadRecord, settings: DownloadSettings): DownloadResolution {
        val remote = controller.connection ?: throw IllegalStateException("offline")
        val profile = record.profileId
        val context = rankingContext(controller.seriesMemoryOf(profile, record.card), controller.sourceMemory.affinity(profile), controller.capability)
        val outcome = remote.sources(record.card, SourceReuseContext(), context.device)
        val ranked = remote.rankSources(profile, outcome.candidates, context)
        val pinned = record.source?.takeIf { record.pinned }
        if (pinned != null) {
            val candidate = sameRelease(pinned, ranked.rows) ?: pinned
            downloadBlockReason(candidate)?.let { throw DownloadUnavailable(it) }
            val delivery = try { remote.admitDownload(candidate) }
                catch (_: DownloadSourceUnsupported) { throw DownloadUnavailable(DOWNLOAD_NEEDS_CONVERSION) }
            controller.rememberStartedSource(profile, record.card, candidate)
            return DownloadResolution(candidate, delivery.url, delivery.headers, delivery.mimeType
                ?: org.viptv.app.player.playbackMimeType(candidate.presentation?.delivery_format), delivery.sizeBytes)
        }
        val candidates = downloadCandidates(ranked, settings.maxQualityHeight)
        val ordered = downloadOrder(candidates, record.source, record.release)
        for (candidate in ordered) {
            val delivery = try { remote.admitDownload(candidate) } catch (_: DownloadSourceUnsupported) { continue }
            controller.rememberStartedSource(profile, record.card, candidate)
            return DownloadResolution(candidate, delivery.url, delivery.headers, delivery.mimeType
                ?: org.viptv.app.player.playbackMimeType(candidate.presentation?.delivery_format), delivery.sizeBytes)
        }
        throw DownloadUnavailable(if (candidates.isEmpty()) "No source for this title can be downloaded at this quality."
            else "No source for this title can be downloaded to this device.")
    }
}

internal class RemoteDownloadSubtitles(private val controller: ApplicationController) : DownloadSubtitleFetcher {
    override fun accepts(profileId: String): Boolean =
        controller.connection != null && controller.mutable.value.selectedProfile == profileId

    override suspend fun fetch(record: DownloadRecord): List<Pair<org.viptv.engine.wire.SubtitleCandidate, ByteArray>> {
        val remote = controller.connection ?: throw IllegalStateException("offline")
        val preferences = controller.mutable.value.preferences?.preferences ?: ProfilePreferences()
        val chosen = downloadSubtitleChoice(remote.subtitles(record.card).candidates, preferences, record.source)
        return chosen.map { it to remote.subtitleContent(it.id).encodeToByteArray() }
    }
}
