package org.viptv.app.downloads

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.json.Json
import org.viptv.app.connection.epochMillis
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SubtitleCandidate

enum class DownloadNetwork { None, Metered, Unmetered }

internal interface DownloadFiles {
    fun size(name: String): Long
    fun open(name: String, append: Boolean): DownloadSink
    fun write(name: String, bytes: ByteArray)
    fun read(name: String): ByteArray?
    fun delete(name: String)
    fun path(name: String): String
    fun freeBytes(): Long
    fun readBook(): String?
    fun writeBook(text: String)
}

internal interface DownloadSink {
    fun write(bytes: ByteArray, count: Int)
    fun close()
}

internal class DownloadResolution(val source: SourceCandidate, val url: String, val headers: Map<String, String>,
    val mimeType: String?, val sizeBytes: Long?)

internal fun interface DownloadResolver {
    fun accepts(profileId: String): Boolean = true
    suspend fun resolve(record: DownloadRecord, settings: DownloadSettings): DownloadResolution
}

internal interface DownloadSubtitleFetcher {
    fun accepts(profileId: String): Boolean
    suspend fun fetch(record: DownloadRecord): List<Pair<SubtitleCandidate, ByteArray>>
}

internal interface DownloadSegmentFetcher {
    fun accepts(profileId: String): Boolean
    suspend fun fetch(record: DownloadRecord): List<DownloadSegment>
}

internal fun interface DownloadProbe {
    fun tracks(path: String): List<DownloadTrack>
}

internal class DownloadUnavailable(message: String) : Exception(message)

internal class DownloadDeferred(val retryAfterMillis: Long) : Exception()

private class ExpiredUrl : Exception()
private class OutOfSpace : Exception()
private class TransferFailed(val status: Int) : Exception("The download server answered $status.")

internal class DownloadRequest(val profileId: String, val card: Card, val seriesTitle: String?, val smart: Boolean = false,
    val source: SourceCandidate? = null, val release: SourceCandidate? = null)

internal class DownloadEngine(
    private val files: DownloadFiles,
    private val client: HttpClient,
    val network: StateFlow<DownloadNetwork>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::epochMillis,
    private val retryDelays: List<Long> = listOf(2_000L, 10_000L, 30_000L),
    private val sweepMillis: Long = 60_000L,
    private val saveDelayMillis: Long = 1_000L,
    private val probe: DownloadProbe? = null,
) : Downloads {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = SynchronizedObject()
    private var book = load()
    private val itemFlow = MutableStateFlow(emptyList<DownloadItem>())
    private val storageFlow = MutableStateFlow(DownloadStorage(0, 0))
    private val settingsFlow = MutableStateFlow(book.settings.settings())
    private val evictionFlow = MutableStateFlow(book.evicted)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val saves = Channel<Unit>(Channel.CONFLATED)
    private var running: Pair<String, Job>? = null
    private var resolverValue: DownloadResolver? = null
    private var subtitleFetcherValue: DownloadSubtitleFetcher? = null
    private val fetchingSubtitles = mutableSetOf<String>()
    private var segmentFetcherValue: DownloadSegmentFetcher? = null
    private val fetchingSegments = mutableSetOf<String>()
    private val probing = mutableSetOf<String>()

    override val items: StateFlow<List<DownloadItem>> = itemFlow.asStateFlow()
    override val storage: StateFlow<DownloadStorage> = storageFlow.asStateFlow()
    override val settings: StateFlow<DownloadSettings> = settingsFlow.asStateFlow()
    override val evictionNotice: StateFlow<Int> = evictionFlow.asStateFlow()

    var resolver: DownloadResolver?
        get() = resolverValue
        set(value) { resolverValue = value; requeueWaiting(); wake.trySend(Unit) }

    var subtitleFetcher: DownloadSubtitleFetcher?
        get() = subtitleFetcherValue
        set(value) { subtitleFetcherValue = value; fetchPendingSubtitles() }

    var segmentFetcher: DownloadSegmentFetcher?
        get() = segmentFetcherValue
        set(value) { segmentFetcherValue = value; fetchPendingSegments() }

    var playingId: String? = null
        private set

    init {
        publish()
        scope.launch { network.drop(1).collect { requeueWaiting(); wake.trySend(Unit); fetchPendingSubtitles(); fetchPendingSegments() } }
        scope.launch { while (true) { saves.receive(); save(); delay(saveDelayMillis) } }
        scope.launch { while (true) { sweep(); delay(sweepMillis) } }
        scope.launch { work() }
        records().filter { it.state == DownloadState.Completed && !it.probed }.forEach(::probeTracks)
    }

    fun probeIfMissing(id: String) {
        record(id)?.takeIf { it.state == DownloadState.Completed && !it.probed }?.let(::probeTracks)
    }

    fun wakeUp() { wake.trySend(Unit); fetchPendingSubtitles() }

    fun records(): List<DownloadRecord> = synchronized(lock) { book.records }

    fun record(id: String): DownloadRecord? = records().firstOrNull { it.id == id }

    fun enqueue(requests: List<DownloadRequest>): List<String> {
        val now = clock()
        val ids = requests.map { downloadId(it.profileId, it.card) }
        mutate { current ->
            val known = current.records.associateBy { it.id }
            val added = requests.mapIndexedNotNull { index, request ->
                val id = ids[index]
                val prior = known[id]
                if (prior != null && prior.state != DownloadState.Failed) null
                else DownloadRecord(id, request.profileId, request.card, request.seriesTitle, addedAtMillis = now + index, smart = request.smart,
                    source = request.source, pinned = request.source != null, release = request.release ?: request.source)
            }
            val replaced = added.map { it.id }.toSet()
            current.copy(records = current.records.filter { it.id !in replaced } + added)
        }
        wake.trySend(Unit)
        return ids
    }

    override fun updateSettings(settings: DownloadSettings) {
        mutate { it.copy(settings = settings.record()) }
        requeueWaiting()
        sweep()
        wake.trySend(Unit)
        fetchPendingSubtitles()
    }

    fun replaceSource(id: String, source: SourceCandidate): Boolean {
        val current = record(id) ?: return false
        if (current.state != DownloadState.Failed && current.source != null && sameRelease(current.source, listOf(source)) != null) return false
        stopRunning(id)
        change(id) { record ->
            val kept = record.previous ?: record.takeIf { it.state == DownloadState.Completed }?.let {
                DownloadPrevious(it.revision, it.source, it.mimeType, it.bytesDone, it.subtitles, it.tracks, it.subtitlesFetched)
            }
            record.copy(revision = maxOf(record.revision, kept?.revision ?: 0) + 1, previous = kept, source = source, pinned = true,
                release = source, url = null, headers = emptyMap(), mimeType = null, state = DownloadState.Queued, bytesDone = 0,
                bytesTotal = null, failure = null, retryAtMillis = null, subtitles = emptyList(), tracks = emptyList(), subtitlesFetched = false)
        } ?: return false
        if (current.state != DownloadState.Completed) {
            files.delete(current.fileName)
            current.subtitles.forEach { files.delete(it.fileName) }
        }
        wake.trySend(Unit)
        return true
    }

    override fun pause(id: String) {
        change(id) { if (it.state.active) it.copy(state = DownloadState.Paused) else it }
        stopRunning(id)
    }

    override fun resume(id: String) {
        change(id) {
            if (it.state == DownloadState.Paused || it.state == DownloadState.Failed || it.state == DownloadState.WaitingForSpace)
                it.copy(state = DownloadState.Queued, failure = null) else it
        }
        wake.trySend(Unit)
    }

    override fun cancel(id: String) = delete(id)

    override fun delete(id: String) {
        stopRunning(id)
        val removed = synchronized(lock) { book.records.firstOrNull { it.id == id } } ?: return
        mutate { it.copy(records = it.records.filter { record -> record.id != id }) }
        erase(removed)
        requeueWaiting()
        wake.trySend(Unit)
    }

    override fun keep(id: String, keep: Boolean) {
        change(id) { it.copy(keep = keep) }
        if (!keep) sweep()
    }

    override fun dismissEvictionNotice() = mutate { it.copy(evicted = 0) }

    override fun localFile(id: String): String? =
        record(id)?.takeIf { it.state == DownloadState.Completed }?.let { files.path(it.fileName) }

    fun subtitleBytes(id: String, subtitleId: String): ByteArray? =
        record(id)?.subtitles?.firstOrNull { it.id == subtitleId }?.let { files.read(it.fileName) }

    fun storeSubtitle(id: String, subtitle: SubtitleCandidate, content: ByteArray) {
        val record = record(id) ?: return
        val name = downloadFileName("${record.fileName}|subtitle|${subtitle.id}").removeSuffix(".media") + ".sub"
        files.write(name, content)
        change(record.id) { current ->
            current.copy(subtitles = current.subtitles.filter { it.id != subtitle.id } +
                DownloadSubtitle(subtitle.id, subtitle.provider_id, subtitle.language, subtitle.label, name))
        }
    }

    fun storeSegments(id: String, segments: List<DownloadSegment>) {
        change(id) { it.copy(segments = segments) }
    }

    fun playing(id: String?) {
        playingId = id
        if (id != null) change(id) { it.copy(lastPlayedAtMillis = clock()) }
        else sweep()
    }

    fun played(id: String, positionMillis: Long, durationMillis: Long?, watched: Boolean) {
        change(id) {
            it.copy(positionMillis = positionMillis, durationMillis = durationMillis ?: it.durationMillis, lastPlayedAtMillis = clock(),
                watchedAtMillis = if (watched) it.watchedAtMillis ?: clock() else it.watchedAtMillis)
        }
    }

    fun queueProgress(progress: OfflineProgress) = mutate { current ->
        current.copy(progress = current.progress.filterNot { it.profileId == progress.profileId && it.card.identity == progress.card.identity } + progress)
    }

    fun pendingProgress(): List<OfflineProgress> = synchronized(lock) { book.progress }

    fun settleProgress(sent: OfflineProgress) = mutate { current -> current.copy(progress = current.progress.filterNot { it == sent }) }

    fun sweep() {
        val now = clock()
        val gone = synchronized(lock) { expired(book.records, book.settings.settings(), now, playingId) }
        if (gone.isEmpty()) return
        val ids = gone.map { it.id }.toSet()
        mutate { it.copy(records = it.records.filter { record -> record.id !in ids }) }
        gone.forEach(::erase)
        requeueWaiting()
        wake.trySend(Unit)
    }

    private suspend fun work() {
        while (true) {
            val next = pick()
            if (next == null) {
                val retryAt = records().filter { it.state == DownloadState.Queued }.mapNotNull { it.retryAtMillis }.minOrNull()
                if (retryAt == null) wake.receive()
                else withTimeoutOrNull((retryAt - clock()).coerceAtLeast(1)) { wake.receive() }
                continue
            }
            val job = scope.launch { run(next) }
            synchronized(lock) { running = next.id to job }
            job.join()
            synchronized(lock) { if (running?.first == next.id) running = null }
        }
    }

    private fun allowed(settings: DownloadSettings): Boolean = when (network.value) {
        DownloadNetwork.None -> false
        DownloadNetwork.Metered -> !settings.wifiOnly
        DownloadNetwork.Unmetered -> true
    }

    private fun pick(): DownloadRecord? {
        val waiting = setOf(DownloadState.Queued, DownloadState.WaitingForNetwork, DownloadState.Resolving, DownloadState.Downloading)
        val pending = records().filter { it.state in waiting }.sortedBy { it.addedAtMillis }
        if (pending.isEmpty()) return null
        if (!allowed(settingsFlow.value)) {
            pending.forEach { record -> change(record.id) { it.copy(state = DownloadState.WaitingForNetwork) } }
            return null
        }
        val now = clock()
        return pending.firstOrNull { (it.retryAtMillis ?: 0) <= now && (it.url != null || resolverValue?.accepts(it.profileId) == true) }
    }

    private suspend fun run(start: DownloadRecord) {
        var record = start.copy(bytesDone = files.size(start.fileName))
        var attempt = 0
        var expirations = 0
        while (true) {
            try {
                if (record.url == null) record = resolve(record)
                ensureSpace(record)
                record = transfer(record)
                return
            } catch (error: CancellationException) { throw error }
            catch (error: DownloadUnavailable) { fail(record.id, error.message); return }
            catch (error: DownloadDeferred) {
                change(record.id) { it.copy(state = DownloadState.Queued, retryAtMillis = clock() + error.retryAfterMillis) }
                return
            }
            catch (_: OutOfSpace) {
                change(record.id) { it.copy(state = DownloadState.WaitingForSpace) }
                return
            }
            catch (_: ExpiredUrl) {
                record = (record(record.id) ?: return).copy(url = null)
                change(record.id) { it.copy(url = null, headers = emptyMap()) }
                if (++expirations > 2) { fail(record.id, "The source link kept expiring. Try again later."); return }
                if (resolverValue?.accepts(record.profileId) != true) { change(record.id) { it.copy(state = DownloadState.Queued) }; return }
            }
            catch (error: Exception) {
                record = record(record.id) ?: return
                if (network.value == DownloadNetwork.None || !allowed(settingsFlow.value)) {
                    change(record.id) { it.copy(state = DownloadState.WaitingForNetwork) }
                    return
                }
                if (attempt >= retryDelays.size) {
                    fail(record.id, (error as? TransferFailed)?.message ?: "The download stopped. Try again.")
                    return
                }
                delay(retryDelays[attempt++])
            }
        }
    }

    private suspend fun resolve(record: DownloadRecord): DownloadRecord {
        val resolver = resolverValue?.takeIf { it.accepts(record.profileId) } ?: throw ExpiredUrl()
        change(record.id) { it.copy(state = DownloadState.Resolving, failure = null, retryAtMillis = null) }
        val resolution = resolver.resolve(record, settingsFlow.value)
        val sameRelease = record.source != null && sameRelease(record.source, listOf(resolution.source)) != null
        val keepBytes = record.bytesDone > 0 && sameRelease
        if (!keepBytes && record.bytesDone > 0) files.delete(record.fileName)
        val total = resolution.sizeBytes ?: resolution.source.presentation?.size_bytes ?: record.bytesTotal.takeIf { keepBytes }
        return change(record.id) {
            it.copy(state = DownloadState.Downloading, source = resolution.source, url = resolution.url, headers = resolution.headers,
                mimeType = resolution.mimeType, bytesTotal = total, bytesDone = if (keepBytes) it.bytesDone else 0)
        } ?: throw CancellationException("removed")
    }

    private fun ensureSpace(record: DownloadRecord, grown: Boolean = false) {
        val records = records()
        val used = records.sumOf { it.storedBytes }
        val limit = effectiveLimit(settingsFlow.value, used, files.freeBytes())
        val needed = if (grown) 0 else ((record.bytesTotal ?: 0) - record.bytesDone).coerceAtLeast(0)
        val plan = evictionPlan(records, protectedIds(records, playingId) + record.id, needed, limit) ?: throw OutOfSpace()
        if (plan.isEmpty()) return
        val ids = plan.map { it.id }.toSet()
        mutate { it.copy(records = it.records.filter { r -> r.id !in ids }, evicted = it.evicted + plan.size) }
        plan.forEach(::erase)
    }

    private suspend fun transfer(start: DownloadRecord): DownloadRecord {
        var record = change(start.id) { it.copy(state = DownloadState.Downloading, bytesDone = start.bytesDone) } ?: return start
        val url = requireNotNull(record.url)
        client.prepareGet(url) {
            record.headers.forEach { (key, value) -> header(key, value) }
            if (record.bytesDone > 0) header(HttpHeaders.Range, "bytes=${record.bytesDone}-")
        }.execute { response ->
            val status = response.status.value
            when {
                status == 401 || status == 403 || status == 404 || status == 410 -> throw ExpiredUrl()
                status == 416 -> {
                    val total = record.bytesTotal
                    if (total != null && record.bytesDone >= total) { record = complete(record.id) ?: record; return@execute }
                    files.delete(record.fileName)
                    change(record.id) { it.copy(bytesDone = 0) }
                    throw TransferFailed(status)
                }
                status !in 200..299 -> throw TransferFailed(status)
            }
            val resumed = status == 206 && record.bytesDone > 0
            val total = if (status == 206) response.headers[HttpHeaders.ContentRange]?.substringAfter('/')?.toLongOrNull()
                else response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            var done = if (resumed) record.bytesDone else 0L
            record = change(record.id) { it.copy(bytesDone = done, bytesTotal = total ?: it.bytesTotal) } ?: return@execute
            val channel = response.bodyAsChannel()
            val sink = files.open(record.fileName, append = resumed)
            try {
                val buffer = ByteArray(64 * 1024)
                var checked = done
                var reported = done
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    sink.write(buffer, read)
                    done += read
                    if (done - reported >= REPORT_BYTES) {
                        reported = done
                        change(record.id) { it.copy(bytesDone = done) } ?: throw CancellationException("removed")
                    }
                    if (done - checked >= SPACE_CHECK_BYTES) {
                        checked = done
                        ensureSpace(record(record.id) ?: return@execute, grown = true)
                    }
                }
            } finally {
                sink.close()
                change(record.id) { it.copy(bytesDone = done) }
            }
            val expected = record(record.id)?.bytesTotal
            if (expected != null && done < expected) throw TransferFailed(status)
            record = complete(record.id) ?: record
        }
        return record
    }

    private fun complete(id: String): DownloadRecord? {
        val before = record(id)
        val done = change(id) { it.copy(state = DownloadState.Completed, bytesTotal = it.bytesDone, failure = null, previous = null) } ?: return null
        before?.previous?.let { previous ->
            before.previousFileName?.let(files::delete)
            previous.subtitles.forEach { files.delete(it.fileName) }
        }
        probeTracks(done)
        fetchSubtitles(done)
        fetchSegments(done)
        return done
    }

    private fun fail(id: String, message: String?) {
        val record = record(id) ?: return
        val previous = record.previous
        if (previous == null) {
            change(id) { it.copy(state = DownloadState.Failed, failure = message ?: "The download failed.") }
            return
        }
        files.delete(record.fileName)
        record.subtitles.forEach { files.delete(it.fileName) }
        change(id) {
            it.copy(state = DownloadState.Completed, revision = previous.revision, source = previous.source, mimeType = previous.mimeType,
                bytesDone = previous.bytes, bytesTotal = previous.bytes, subtitles = previous.subtitles, tracks = previous.tracks,
                subtitlesFetched = previous.subtitlesFetched, previous = null, url = null, headers = emptyMap(), retryAtMillis = null,
                failure = "Couldn't switch source: ${message ?: "the download failed."}")
        }
    }

    private fun probeTracks(record: DownloadRecord) {
        val probe = probe ?: return
        if (!synchronized(lock) { probing.add(record.id) }) return
        val path = files.path(record.fileName)
        scope.launch {
            try {
                val tracks = try { probe.tracks(path) } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
                change(record.id) { if (it.revision == record.revision) it.copy(tracks = tracks ?: it.tracks, probed = true) else it }
            } finally {
                synchronized(lock) { probing.remove(record.id) }
            }
        }
    }

    private fun fetchPendingSubtitles() {
        records().filter { it.state == DownloadState.Completed && !it.subtitlesFetched }.forEach(::fetchSubtitles)
    }

    private fun fetchSubtitles(record: DownloadRecord) {
        if (!settingsFlow.value.downloadSubtitles || record.subtitlesFetched) return
        val fetcher = subtitleFetcherValue?.takeIf { it.accepts(record.profileId) } ?: return
        if (network.value == DownloadNetwork.None) return
        if (!synchronized(lock) { fetchingSubtitles.add(record.id) }) return
        scope.launch {
            try {
                val fetched = try { fetcher.fetch(record) } catch (error: CancellationException) { throw error } catch (_: Exception) { return@launch }
                if (record(record.id)?.revision != record.revision) return@launch
                fetched.forEach { (subtitle, content) -> if (content.isNotEmpty()) storeSubtitle(record.id, subtitle, content) }
                change(record.id) { if (it.revision == record.revision) it.copy(subtitlesFetched = true) else it }
            } finally {
                synchronized(lock) { fetchingSubtitles.remove(record.id) }
            }
        }
    }

    private fun fetchPendingSegments() {
        records().filter { it.state == DownloadState.Completed && it.segments == null }.forEach(::fetchSegments)
    }

    private fun fetchSegments(record: DownloadRecord) {
        if (record.segments != null) return
        val fetcher = segmentFetcherValue?.takeIf { it.accepts(record.profileId) } ?: return
        if (network.value == DownloadNetwork.None) return
        if (!synchronized(lock) { fetchingSegments.add(record.id) }) return
        scope.launch {
            try {
                val fetched = try { fetcher.fetch(record) } catch (error: CancellationException) { throw error } catch (_: Exception) { return@launch }
                storeSegments(record.id, fetched)
            } finally {
                synchronized(lock) { fetchingSegments.remove(record.id) }
            }
        }
    }

    private fun stopRunning(id: String) {
        val job = synchronized(lock) { running?.takeIf { it.first == id }?.second }
        job?.cancel()
    }

    private fun requeueWaiting() = mutate { current ->
        current.copy(records = current.records.map {
            if (it.state == DownloadState.WaitingForSpace || it.state == DownloadState.WaitingForNetwork) it.copy(state = DownloadState.Queued) else it
        })
    }

    private fun erase(record: DownloadRecord) {
        files.delete(record.fileName)
        record.subtitles.forEach { files.delete(it.fileName) }
        record.previousFileName?.let(files::delete)
        record.previous?.subtitles?.forEach { files.delete(it.fileName) }
    }

    private fun change(id: String, transform: (DownloadRecord) -> DownloadRecord): DownloadRecord? {
        var changed: DownloadRecord? = null
        mutate { current ->
            current.copy(records = current.records.map { record ->
                if (record.id == id) transform(record).also { changed = it } else record
            })
        }
        return changed
    }

    private fun mutate(transform: (DownloadBook) -> DownloadBook) {
        val changed = synchronized(lock) {
            val next = transform(book)
            if (next == book) false else { book = next; true }
        }
        if (!changed) return
        publish()
        saves.trySend(Unit)
    }

    private fun publish() {
        val current = synchronized(lock) { book }
        val settings = current.settings.settings()
        settingsFlow.value = settings
        itemFlow.value = current.records.map { it.item(settings) }
        storageFlow.value = DownloadStorage(current.records.sumOf { it.storedBytes }, files.freeBytes())
        evictionFlow.value = current.evicted
    }

    private fun save() {
        val current = synchronized(lock) { book }
        runCatching { files.writeBook(codec.encodeToString(DownloadBook.serializer(), current)) }
    }

    fun flush() = save()

    private fun load(): DownloadBook {
        val stored = runCatching { files.readBook()?.let { codec.decodeFromString(DownloadBook.serializer(), it) } }.getOrNull() ?: DownloadBook()
        return stored.copy(records = stored.records.map { record ->
            val state = if (record.state == DownloadState.Resolving || record.state == DownloadState.Downloading) DownloadState.Queued else record.state
            record.copy(state = state, bytesDone = files.size(record.fileName))
        })
    }

    private companion object {
        const val SPACE_CHECK_BYTES = 8L shl 20
        const val REPORT_BYTES = 512L shl 10
    }
}

