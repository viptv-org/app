package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import org.viptv.app.connection.*
import org.viptv.app.downloads.*
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

private const val DOWNLOAD_PLAYBACK_PREFIX = "download:"
private const val DOWNLOAD_GONE = "This download is no longer on this device."
private const val WATCHED_FRACTION = 0.95
private const val EPISODE_PAGE_LIMIT = 128
private const val EPISODE_PAGE_COUNT = 40

val ApplicationController.downloads: Downloads? get() = downloadEngine

internal fun ApplicationController.startDownloads() {
    val engine = downloadEngine ?: return
    engine.resolver = RemoteDownloadResolver(this)
    engine.subtitleFetcher = RemoteDownloadSubtitles(this)
    engine.segmentFetcher = RemoteDownloadSegments(this)
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

fun ApplicationController.downloadOpened(id: String) { downloadEngine?.probeIfMissing(id) }

fun ApplicationController.playDownload(id: String) = playDownload(id, startOver = false)

internal fun ApplicationController.playDownload(id: String, startOver: Boolean) {
    val record = downloadEngine?.record(id)?.takeIf { it.state == DownloadState.Completed } ?: return
    val source = record.localSource()
    val before = mutable.value
    val returnTo = before.screen.takeIf { it != Screen.Player } ?: downloadReturn ?: before.detailReturnScreen
    cancelSeekReopen()
    job?.cancel()
    clearBestMatchSession()
    if (before.playback != null && !completionSeen) saveProgress(false, final = true)
    releasePlaybackSession()
    downloadReturn = returnTo
    downloadWatchNoted = null
    val keptDetail = before.detail?.takeIf { !before.detailShell && sameLookupIdentity(it.item.identity, record.card.parent_identity ?: record.card.identity) }
    updateState { it.copy(sourceTitle = record.card, detail = keptDetail, playingSource = source, screen = Screen.Player, presentation = null,
        error = null, playbackFailure = null, pendingAction = null, navForward = emptyList(), detailReturnScreen = returnTo) }
    openSource(source, forceGateway = false, positionMillis = if (startOver) 0L else null, localCopy = id)
    if (keptDetail == null) loadLocalPlaybackDetail(record.card)
}

private fun DownloadRecord.localSource(): SourceCandidate = source ?: SourceCandidate(id, "download", card.title)

internal fun ApplicationController.downloadsOnline(): Boolean =
    connection != null && downloadEngine?.network?.value != DownloadNetwork.None

internal suspend fun ApplicationController.openDownload(id: String, profile: String, positionMillis: Long?): PlaybackInput {
    val engine = requireNotNull(downloadEngine)
    val record = engine.record(id)?.takeIf { it.state == DownloadState.Completed && it.profileId == profile } ?: throw LocalFailure(DOWNLOAD_GONE)
    val path = engine.localFile(id) ?: throw LocalFailure(DOWNLOAD_GONE)
    engine.probeIfMissing(id)
    val resume = positionMillis ?: localResume(record, profile)
    engine.playing(id)
    val input = PlaybackInput("$DOWNLOAD_PLAYBACK_PREFIX${kotlin.uuid.Uuid.random()}:$id", "file://$path", positionMillis = resume, profileId = profile,
        durationMillis = record.durationMillis, mimeType = record.mimeType)
    input.updateProviderSubtitles(record.subtitles.map { it.candidate() })
    return input
}

private suspend fun ApplicationController.localResume(record: DownloadRecord, profile: String): Long {
    val stored = record.positionMillis.takeIf { record.watchedAtMillis == null && it > 0 } ?: 0L
    val unsynced = downloadEngine?.pendingProgress()?.lastOrNull { it.profileId == profile && sameLookupIdentity(it.card.identity, record.card.identity) }
    if (unsynced != null) return if (unsynced.ended) 0L else unsynced.positionMillis
    val remote = connection?.takeIf { downloadsOnline() } ?: return stored
    val saved = try { withTimeoutOrNull(5_000) { remote.savedPosition(profile, record.card) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    return saved ?: stored
}

internal suspend fun ApplicationController.downloadSegments(input: PlaybackInput, card: Card): List<TvSkipRange> {
    val engine = downloadEngine ?: return emptyList()
    val id = input.downloadId() ?: return emptyList()
    val stored = engine.record(id)?.segments.orEmpty().mapNotNull { it.skipRange() }
    val remote = connection?.takeIf { downloadsOnline() } ?: return stored
    val fetched = try { remote.titleSegments(card) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { return stored }
    engine.storeSegments(id, fetched.map { it.downloadSegment() })
    return fetched
}

internal suspend fun ApplicationController.downloadSubtitleContent(input: PlaybackInput, card: Card, subtitleId: String): ByteArray {
    val id = requireNotNull(input.downloadId())
    downloadEngine?.subtitleBytes(id, subtitleId)?.let { return it }
    val remote = connection?.takeIf { downloadsOnline() } ?: return ByteArray(0)
    return remote.subtitleContent(subtitleId).encodeToByteArray().also { content -> storeDownloadSubtitle(input, card, subtitleId, content) }
}

private fun ApplicationController.loadLocalPlaybackDetail(card: Card) {
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection?.takeIf { downloadsOnline() }
    val device = local
    if (remote == null && device == null) return
    val parent = card.parent_identity ?: return
    scope.launch {
        val detail = try { withTimeoutOrNull(12_000) { device?.detail(profile, card) ?: requireNotNull(remote).detail(card) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null } ?: return@launch
        val state = mutable.value
        if (state.selectedProfile != profile || state.screen != Screen.Player || state.sourceTitle?.let(::cardLookupKey) != cardLookupKey(card) ||
            !sameLookupIdentity(detail.item.identity, parent)) return@launch
        updateState { it.copy(detail = detail.withCardPresentation(card).withOpenedProgress(card), detailShell = false) }
        loadPlaybackSeason()
    }
}

private fun TvSkipRange.downloadSegment() = DownloadSegment(kind.name.lowercase(), startMillis, endMillis, mixed)

private fun DownloadSegment.skipRange(): TvSkipRange? {
    val kind = TvSkipKind.entries.firstOrNull { it.name.lowercase() == kind } ?: return null
    return TvSkipRange(startMillis, endMillis, kind, mixed).takeIf { endMillis > startMillis }
}

internal fun PlaybackInput.downloadId(): String? =
    id.takeIf { it.startsWith(DOWNLOAD_PLAYBACK_PREFIX) }?.removePrefix(DOWNLOAD_PLAYBACK_PREFIX)?.substringAfter(':')

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

internal fun ApplicationController.closeDownloadScreen(): Screen? = downloadReturn.also { downloadReturn = null }

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
    engine.record(downloadId(profile, card))?.takeIf { it.state == DownloadState.Completed }?.let { engine.played(it.id, positionMillis, durationMillis, watched) }
    if (watched) noteWatched(input, profile, card)
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

internal class RemoteDownloadSegments(private val controller: ApplicationController) : DownloadSegmentFetcher {
    override fun accepts(profileId: String): Boolean =
        controller.connection != null && controller.mutable.value.selectedProfile == profileId

    override suspend fun fetch(record: DownloadRecord): List<DownloadSegment> {
        val remote = controller.connection ?: throw IllegalStateException("offline")
        return remote.titleSegments(record.card).map { it.downloadSegment() }
    }
}
