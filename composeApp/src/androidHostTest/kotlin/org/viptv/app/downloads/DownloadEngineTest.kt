package org.viptv.app.downloads

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import org.viptv.engine.wire.SubtitleCandidate
import java.util.Collections
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadEngineTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private val requests = Collections.synchronizedList(mutableListOf<HttpRequestData>())
    private val network = MutableStateFlow(DownloadNetwork.Unmetered)
    private var now = 1_000_000L

    @After fun close() = scope.cancel()

    @Test fun queueResolveAndResumeAfterAnInterruptionWithRange() = runBlocking<Unit> {
        val files = MemoryFiles()
        var cut = true
        val engine = engine(files, { url(1) }) { request ->
            val range = request.headers[HttpHeaders.Range]
            if (cut) { cut = false; respond(payload.copyOf(100_000), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString())) }
            else serve(range)
        }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        val done = engine.awaitState(id, DownloadState.Completed)
        assertEquals(payload.size.toLong(), done.bytesDone)
        assertContentEquals(payload, files.bytes(engine.record(id)!!.fileName))
        assertEquals("bytes=100000-", requests.last().headers[HttpHeaders.Range])
        assertEquals(files.path(engine.record(id)!!.fileName), engine.localFile(id))
    }

    @Test fun anExpiredUrlResolvesTheSameReleaseAgainAndContinues() = runBlocking<Unit> {
        val files = MemoryFiles()
        var resolutions = 0
        val engine = engine(files, { resolutions++; url(resolutions) }) { request ->
            val range = request.headers[HttpHeaders.Range]
            when {
                request.url.toString().endsWith("/1") && range == null ->
                    respond(payload.copyOf(120_000), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString()))
                request.url.toString().endsWith("/1") -> respond("", HttpStatusCode.Forbidden)
                else -> serve(range)
            }
        }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(id, DownloadState.Completed)
        assertEquals(2, resolutions)
        assertEquals("bytes=120000-", requests.last().headers[HttpHeaders.Range])
        assertContentEquals(payload, files.bytes(engine.record(id)!!.fileName))
    }

    @Test fun wifiOnlyWaitsOnAMeteredNetwork() = runBlocking<Unit> {
        network.value = DownloadNetwork.Metered
        val engine = engine(MemoryFiles(), { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(id, DownloadState.WaitingForNetwork)
        assertTrue(requests.isEmpty())
        network.value = DownloadNetwork.Unmetered
        engine.awaitState(id, DownloadState.Completed)
    }

    @Test fun aFullLimitEvictsWatchedThenWaitsForSpaceAndResumesWhenSpaceFrees() = runBlocking<Unit> {
        val files = MemoryFiles()
        val engine = engine(files, { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        val first = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(first, DownloadState.Completed)
        val kept = engine.enqueue(listOf(DownloadRequest("p", episode(2), "Show"))).single()
        engine.awaitState(kept, DownloadState.Completed)
        engine.keep(kept, true)
        engine.played(first, 1, 1, watched = true)
        engine.updateSettings(DownloadSettings(storageLimitBytes = payload.size * 2L, deleteWatchedAfterMillis = null))

        val third = engine.enqueue(listOf(DownloadRequest("p", episode(3), "Show"))).single()
        engine.awaitState(third, DownloadState.Completed)
        assertNull(engine.record(first))
        assertEquals(1, engine.evictionNotice.value)

        val fourth = engine.enqueue(listOf(DownloadRequest("p", movie(), null))).single()
        engine.awaitState(fourth, DownloadState.Completed)
        assertNull(engine.record(third))
        engine.playing(fourth)
        val fifth = engine.enqueue(listOf(DownloadRequest("p", episode(5), "Show"))).single()
        engine.awaitState(fifth, DownloadState.WaitingForSpace)
        assertEquals(DownloadState.Completed, engine.record(kept)?.state)

        engine.delete(kept)
        engine.awaitState(fifth, DownloadState.Completed)
        engine.dismissEvictionNotice()
        assertEquals(0, engine.evictionNotice.value)
    }

    @Test fun watchedDownloadsExpireAfterTheSettingUnlessKept() = runBlocking<Unit> {
        val engine = engine(MemoryFiles(), { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        val a = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        val b = engine.enqueue(listOf(DownloadRequest("p", episode(2), "Show"))).single()
        engine.awaitState(a, DownloadState.Completed); engine.awaitState(b, DownloadState.Completed)
        engine.playing(a); engine.played(a, 10, 10, watched = true)
        engine.played(b, 10, 10, watched = true)
        engine.keep(b, true)
        assertEquals(now + 86_400_000L, engine.items.value.first { it.id == a }.expiresAtMillis)
        assertNull(engine.items.value.first { it.id == b }.expiresAtMillis)

        engine.updateSettings(DownloadSettings(deleteWatchedAfterMillis = 0))
        assertEquals(DownloadState.Completed, engine.record(a)?.state)
        engine.playing(null)
        assertNull(engine.record(a))
        assertEquals(DownloadState.Completed, engine.record(b)?.state)

        for (delayMillis in listOf(3_600_000L, 86_400_000L, 604_800_000L)) {
            val id = engine.enqueue(listOf(DownloadRequest("p", episode(delayMillis.toInt()), "Show"))).single()
            engine.awaitState(id, DownloadState.Completed)
            engine.updateSettings(DownloadSettings(deleteWatchedAfterMillis = delayMillis))
            engine.played(id, 10, 10, watched = true)
            now += delayMillis - 1; engine.sweep()
            assertEquals(DownloadState.Completed, engine.record(id)?.state)
            now += 1; engine.sweep()
            assertNull(engine.record(id))
        }
        engine.updateSettings(DownloadSettings(deleteWatchedAfterMillis = null))
        val never = engine.enqueue(listOf(DownloadRequest("p", movie(), null))).single()
        engine.awaitState(never, DownloadState.Completed)
        engine.played(never, 10, 10, watched = true)
        now += 10L * 604_800_000L; engine.sweep()
        assertEquals(DownloadState.Completed, engine.record(never)?.state)
        engine.keep(b, false)
        assertEquals(DownloadState.Completed, engine.record(b)?.state)
    }

    @Test fun progressAndQueueSurviveARestart() = runBlocking<Unit> {
        val files = MemoryFiles()
        network.value = DownloadNetwork.None
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = engine(files, { url(1) }, firstScope) { serve(it.headers[HttpHeaders.Range]) }
        val id = first.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        first.awaitState(id, DownloadState.WaitingForNetwork)
        first.queueProgress(OfflineProgress("p", episode(1), 5_000, 10_000, ended = false, atMillis = now))
        first.flush()
        firstScope.cancel()
        network.value = DownloadNetwork.Unmetered
        val second = engine(files, { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        second.awaitState(id, DownloadState.Completed)
        assertEquals(5_000, second.pendingProgress().single().positionMillis)
    }

    @Test fun anExplicitSourceIsResolvedAsGiven() = runBlocking<Unit> {
        val picked = source.copy(id = "picked", label = "Show.S01E01.720p.Other", source_fingerprint = "fp-picked")
        val seen = Collections.synchronizedList(mutableListOf<DownloadRecord>())
        val engine = engineResolving(MemoryFiles(), { record -> seen += record; url(1).let { DownloadResolution(record.source!!, it.url, it.headers, it.mimeType, it.sizeBytes) } }) {
            serve(it.headers[HttpHeaders.Range])
        }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show", source = picked))).single()
        val done = engine.awaitState(id, DownloadState.Completed)
        assertTrue(seen.single().pinned)
        assertEquals(picked, seen.single().source)
        assertEquals("Show.S01E01.720p.Other", done.sourceLabel)
        assertEquals("addon", done.sourceProvider)
    }

    @Test fun changingTheSourceKeepsTheOldFileUntilTheNewOneCompletes() = runBlocking<Unit> {
        val files = MemoryFiles()
        val gate = CompletableDeferred<Unit>()
        val replacement = source.copy(id = "s2", label = "Show.S01E01.2160p", source_fingerprint = "fp-2")
        val engine = engineResolving(files, { record -> DownloadResolution(record.source ?: source, "https://media.test/file/${record.revision}", emptyMap(), "video/mp4", payload.size.toLong()) }) { request ->
            if (request.url.toString().endsWith("/1")) gate.await()
            serve(request.headers[HttpHeaders.Range])
        }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(id, DownloadState.Completed)
        val oldFile = engine.record(id)!!.fileName
        assertTrue(engine.replaceSource(id, replacement))
        engine.awaitState(id, DownloadState.Downloading)
        assertContentEquals(payload, files.bytes(oldFile))
        assertEquals(payload.size.toLong(), engine.storage.value.usedBytes)
        gate.complete(Unit)
        val done = engine.awaitState(id, DownloadState.Completed)
        assertEquals("Show.S01E01.2160p", done.sourceLabel)
        assertEquals(0, files.size(oldFile))
        assertContentEquals(payload, files.bytes(engine.record(id)!!.fileName))
        assertNull(engine.record(id)!!.previous)
    }

    @Test fun aFailedSourceChangeRestoresTheOldDownload() = runBlocking<Unit> {
        val files = MemoryFiles()
        val engine = engineResolving(files, { record ->
            if (record.revision > 0) throw DownloadUnavailable("This source can't be downloaded.")
            url(1)
        }) { serve(it.headers[HttpHeaders.Range]) }
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(id, DownloadState.Completed)
        val oldFile = engine.record(id)!!.fileName
        engine.replaceSource(id, source.copy(id = "s2", label = "Other", source_fingerprint = "fp-2"))
        val restored = withTimeout(10_000) { engine.items.first { list -> list.any { it.id == id && it.state == DownloadState.Completed && it.failure != null } } }
            .first { it.id == id }
        assertEquals(source.label, restored.sourceLabel)
        assertEquals(oldFile, engine.record(id)!!.fileName)
        assertContentEquals(payload, files.bytes(oldFile))
        assertTrue(restored.failure!!.contains("can't be downloaded"))
    }

    @Test fun subtitlesAreFetchedAndStoredOnCompletion() = runBlocking<Unit> {
        val files = MemoryFiles()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val engine = engine(files, { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        engine.subtitleFetcher = subtitles(calls)
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        val item = withTimeout(10_000) { engine.items.first { list -> list.any { it.id == id && it.tracks.isNotEmpty() } } }.first { it.id == id }
        assertEquals(listOf(DownloadTrack("subtitle", "en", "English", embedded = false)), item.tracks)
        assertTrue(engine.subtitleBytes(id, "os-en")!!.decodeToString().contains("Hi"))
        assertEquals(listOf(id), calls.toList())
    }

    @Test fun theSubtitleSettingTurnsFetchingOff() = runBlocking<Unit> {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val engine = engine(MemoryFiles(), { url(1) }) { serve(it.headers[HttpHeaders.Range]) }
        engine.updateSettings(DownloadSettings(downloadSubtitles = false))
        engine.subtitleFetcher = subtitles(calls)
        val id = engine.enqueue(listOf(DownloadRequest("p", episode(1), "Show"))).single()
        engine.awaitState(id, DownloadState.Completed)
        engine.wakeUp()
        kotlinx.coroutines.delay(200)
        assertTrue(calls.isEmpty())
        assertTrue(engine.record(id)!!.subtitles.isEmpty())
        engine.updateSettings(DownloadSettings(downloadSubtitles = true))
        withTimeout(10_000) { while (engine.record(id)?.subtitlesFetched != true) kotlinx.coroutines.delay(10) }
        assertEquals(listOf(id), calls.toList())
    }

    @Test fun theTrackProbeFillsTracks() = runBlocking<Unit> {
        val probed = Collections.synchronizedList(mutableListOf<String>())
        val probe = DownloadProbe { path -> probed += path
            listOf(DownloadTrack("audio", "jpn", null, true), DownloadTrack("audio", "eng", null, true), DownloadTrack("subtitle", "eng", null, true)) }
        val engine = engine(MemoryFiles(), { url(1) }, probe = probe) { serve(it.headers[HttpHeaders.Range]) }
        val id = engine.enqueue(listOf(DownloadRequest("p", movie(), null))).single()
        val item = withTimeout(10_000) { engine.items.first { list -> list.any { it.id == id && it.tracks.size == 3 } } }.first { it.id == id }
        assertEquals(listOf("jpn", "eng"), item.tracks.filter { it.kind == "audio" }.map { it.language })
        assertEquals(listOf("/downloads/${engine.record(id)!!.fileName}"), probed.toList())
        assertEquals("mp4", item.format)
    }

    private fun MockRequestHandleScope.serve(range: String?) = if (range == null)
        respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, payload.size.toString()))
    else {
        val start = range.removePrefix("bytes=").substringBefore('-').toInt()
        respond(payload.copyOfRange(start, payload.size), HttpStatusCode.PartialContent,
            headersOf(HttpHeaders.ContentRange, "bytes $start-${payload.size - 1}/${payload.size}"))
    }

    private fun url(n: Int) = DownloadResolution(source, "https://media.test/file/$n", emptyMap(), "video/mp4", payload.size.toLong())

    private fun engine(files: MemoryFiles, resolve: () -> DownloadResolution, owner: CoroutineScope = scope, probe: DownloadProbe? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): DownloadEngine =
        engineResolving(files, { _ -> resolve() }, owner, probe, handler)

    private fun engineResolving(files: MemoryFiles, resolve: suspend (DownloadRecord) -> DownloadResolution, owner: CoroutineScope = scope,
        probe: DownloadProbe? = null, handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData): DownloadEngine {
        val client = HttpClient(MockEngine { request -> requests += request; handler(request) })
        return DownloadEngine(files, client, network, owner, clock = { now }, retryDelays = listOf(10, 10, 10), saveDelayMillis = 10, probe = probe)
            .also { it.resolver = DownloadResolver { record, _ -> resolve(record) } }
    }

    private fun subtitles(calls: MutableList<String>, available: Boolean = true) = object : DownloadSubtitleFetcher {
        override fun accepts(profileId: String) = available
        override suspend fun fetch(record: DownloadRecord): List<Pair<SubtitleCandidate, ByteArray>> {
            calls += record.id
            return listOf(SubtitleCandidate("os-en", "opensubtitles", "en", "English") to "1\n00:00:01,000 --> 00:00:02,000\nHi".encodeToByteArray())
        }
    }

    private suspend fun DownloadEngine.awaitState(id: String, state: DownloadState): DownloadItem =
        withTimeout(10_000) { items.first { list -> list.any { it.id == id && it.state == state } } }.first { it.id == id }

    private val source = SourceCandidate("s1", "addon", "Show.S01E01.1080p", quality_height = 1080, delivery = "http",
        source_fingerprint = "fp", presentation = SourcePresentation(size_bytes = payload.size.toLong()))

    private fun episode(n: Int) = Card(MediaIdentity("tt1:1:$n", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, n.toLong())),
        provider_id = "catalog", title = "Episode $n", badges = emptyList(), parent_identity = MediaIdentity("tt1", "series", emptyList()))

    private fun movie() = Card(MediaIdentity("tt9", "movie", emptyList()), provider_id = "catalog", title = "Movie", badges = emptyList())
}

internal class MemoryFiles : DownloadFiles {
    private val data = Collections.synchronizedMap(mutableMapOf<String, ByteArray>())
    private var book: String? = null
    var free = 1L shl 40

    fun bytes(name: String) = data[name] ?: ByteArray(0)
    override fun size(name: String) = data[name]?.size?.toLong() ?: 0L
    override fun open(name: String, append: Boolean): DownloadSink {
        if (!append) data[name] = ByteArray(0)
        return object : DownloadSink {
            override fun write(bytes: ByteArray, count: Int) { data[name] = (data[name] ?: ByteArray(0)) + bytes.copyOf(count) }
            override fun close() {}
        }
    }
    override fun write(name: String, bytes: ByteArray) { data[name] = bytes }
    override fun read(name: String) = data[name]
    override fun delete(name: String) { data.remove(name) }
    override fun path(name: String) = "/downloads/$name"
    override fun freeBytes() = free
    override fun readBook() = book
    override fun writeBook(text: String) { book = text }
}
