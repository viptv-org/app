package org.viptv.app.state

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Test
import org.viptv.app.connection.DownloadSourceUnsupported
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.admitDownload
import org.viptv.app.connection.configureTransport
import org.viptv.app.downloads.DownloadBook
import org.viptv.app.downloads.DownloadDeferred
import org.viptv.app.downloads.DownloadEngine
import org.viptv.app.downloads.DownloadNetwork
import org.viptv.app.downloads.DownloadRecord
import org.viptv.app.downloads.DownloadSettings
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.DownloadUnavailable
import org.viptv.app.downloads.MemoryFiles
import org.viptv.app.downloads.downloadId
import org.viptv.app.player.PlayerFact
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val network = MutableStateFlow(DownloadNetwork.None)
    private val requests = Collections.synchronizedList(mutableListOf<Pair<HttpRequestData, String>>())
    private var storeReads = 0
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? { storeReads++; return null }
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val series = MediaIdentity("tt1", "series", emptyList())
    private val episodes = (1..4).map { n ->
        Card(MediaIdentity("tt1:1:$n", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, n.toLong())),
            provider_id = "catalog", title = "Episode $n", badges = emptyList(), parent_identity = series)
    }
    private var playbackAnswer: (JsonObject) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to "{}" }
    private val client = HttpClient(MockEngine { request ->
        val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
        requests += request to body
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when {
            request.url.encodedPath == "/api/meta/series/tt1" -> respond(Json.encodeToString(DetailSnapshot.serializer(),
                DetailSnapshot(Card(series, provider_id = "catalog", title = "Show", badges = emptyList()), episodes = episodes,
                    seasons = listOf(EpisodeSeason(1, 4, 0)))), HttpStatusCode.OK, json)
            request.url.encodedPath == "/api/playback" -> playbackAnswer(Json.parseToJsonElement(body).jsonObject)
                .let { (status, text) -> respond(text, status, json) }
            else -> respond("{}", HttpStatusCode.OK, json)
        }
    }) { configureTransport() }

    @After fun stop() = scope.cancel()

    @Test fun anOfflineStartOpensDownloadsWithoutReachingTheServer() = runBlocking<Unit> {
        val engine = engine(MemoryFiles())
        engine.enqueue(listOf(org.viptv.app.downloads.DownloadRequest("12", episodes[0], "Show")))
        val app = ApplicationController(scope, store, downloads = engine)
        app.restore()
        val state = app.state.first { it.screen == Screen.Downloads }
        assertTrue(state.offline)
        assertEquals(1, state.downloads.size)
        assertEquals(0, storeReads)
    }

    @Test fun offlineProgressOnADownloadSyncsWhenTheNetworkReturns() = runBlocking<Unit> {
        val files = MemoryFiles()
        val engine = engine(files, completed(episodes[0]))
        val app = signedIn(engine)
        val id = downloadId("12", episodes[0])
        app.updateState { it.copy(screen = Screen.Downloads) }
        app.playDownload(id)
        val playing = app.state.first { it.screen == Screen.Player }
        assertTrue(playing.playback!!.uri.startsWith("file://"))
        app.observePlayback(PlayerFact.Progress(600_000, 1_000_000))
        app.saveProgress(false)
        assertEquals(600_000, engine.pendingProgress().single().positionMillis)
        assertTrue(progressWrites().isEmpty())

        network.value = DownloadNetwork.Unmetered
        withTimeout(10_000) { while (engine.pendingProgress().isNotEmpty()) kotlinx.coroutines.delay(10) }
        val sent = Json.parseToJsonElement(progressWrites().single()).jsonObject
        assertEquals(600_000, sent.getValue("record").jsonObject.getValue("position_millis").jsonPrimitive.long)

        app.closePlayer()
        assertEquals(Screen.Downloads, app.state.value.screen)
        assertEquals(600_000, engine.record(id)?.positionMillis)
    }

    @Test fun finishingAnEpisodeWithSmartDownloadsQueuesTheNextOnes() = runBlocking<Unit> {
        val engine = engine(MemoryFiles(), completed(episodes[0]))
        engine.updateSettings(DownloadSettings(keepNextEpisodes = 2))
        val app = signedIn(engine)
        val id = downloadId("12", episodes[0])
        app.playDownload(id)
        app.state.first { it.screen == Screen.Player }
        app.observePlayback(PlayerFact.Progress(990_000, 1_000_000))
        app.saveProgress(true)
        withTimeout(10_000) { engine.items.first { items -> items.size == 3 } }
        assertEquals(listOf("Episode 1", "Episode 2", "Episode 3"), engine.items.value.map { it.card.title })
        assertTrue(engine.records().filter { it.id != id }.all { it.smart })
        assertTrue(engine.record(id)?.watchedAtMillis != null)
    }

    @Test fun admissionAsksForADownloadAndMapsTheServerAnswers() = runBlocking<Unit> {
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val source = SourceCandidate("s1", "addon", "Show 1080p")
        val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }
        playbackAnswer = { HttpStatusCode.OK to """{"purpose":"download","status":"ready","delivery":{"kind":"direct","url":"https://cdn.test/a.mkv","headers":{"Referer":"x"},"format":"original","size":42,"expires_at":null}}""" }
        val delivery = remote.admitDownload(source, facts)
        assertEquals("https://cdn.test/a.mkv", delivery.url)
        assertEquals(42, delivery.sizeBytes)
        assertEquals(mapOf("Referer" to "x"), delivery.headers)
        val request = Json.parseToJsonElement(requests.last().second).jsonObject
        assertEquals("download", request.getValue("purpose").jsonPrimitive.content)
        assertTrue("force_gateway" !in request && "resume" !in request && "position" !in request && "prepare" !in request)

        playbackAnswer = { HttpStatusCode.NotAcceptable to """{"error":"x","error_code":"download_unsupported"}""" }
        assertFailsWith<DownloadSourceUnsupported> { remote.admitDownload(source, facts) }
        playbackAnswer = { HttpStatusCode.TooManyRequests to """{"error":"x","error_code":"download_rate_limited"}""" }
        assertFailsWith<DownloadDeferred> { remote.admitDownload(source, facts) }
        playbackAnswer = { HttpStatusCode.Accepted to """{"id":"lease-1","status":"starting","renew_after_seconds":20}""" }
        assertFailsWith<DownloadUnavailable> { remote.admitDownload(source, facts) }
        assertTrue(requests.any { it.first.method == HttpMethod.Delete && it.first.url.encodedPath == "/api/playback/lease-1" })
    }

    @Test fun playAndBestMatchPreferTheDownloadedCopy() = runBlocking<Unit> {
        val engine = engine(MemoryFiles(), completed(episodes[0]))
        val app = signedIn(engine)
        app.playTitle(episodes[0])
        val playing = app.state.first { it.screen == Screen.Player }
        assertTrue(playing.playback!!.uri.startsWith("file://"))
        assertTrue(requests.none { it.first.url.encodedPath.startsWith("/api/sources") || it.first.url.encodedPath == "/api/playback" })
    }

    @Test fun theSourceListMarksTheDownloadedRowAndPlaysItLocally() = runBlocking<Unit> {
        val stored = SourceCandidate("old-id", "addon", "Show 1080p", source_fingerprint = "fp-1")
        val engine = engine(MemoryFiles(), completed(episodes[0]).copy(source = stored))
        val app = signedIn(engine)
        val match = stored.copy(id = "row-1")
        val other = SourceCandidate("row-2", "addon", "Show 720p")
        app.updateState { it.copy(screen = Screen.Sources, sourceTitle = episodes[0], sources = listOf(other, match)) }
        val marked = withTimeout(10_000) { app.state.first { it.downloadedSources.isNotEmpty() } }
        assertEquals(mapOf("row-1" to downloadId("12", episodes[0])), marked.downloadedSources)
        assertEquals(emptyMap(), marked.downloadableSources)
        app.playSource(match)
        assertTrue(app.state.first { it.screen == Screen.Player }.playback!!.uri.startsWith("file://"))
    }

    @Test fun downloadModeBlocksUnsupportedRowsAndDownloadsThePickedSource() = runBlocking<Unit> {
        val engine = engine(MemoryFiles())
        val app = signedIn(engine)
        val torrent = SourceCandidate("t", "torrentio", "Show 2160p", delivery = "torrent")
        val picked = SourceCandidate("d", "addon", "Show 1080p", binge_group = "group-a")
        app.updateState { it.copy(screen = Screen.Sources, sourceReturnScreen = Screen.Details, sourceTitle = episodes[1],
            sources = listOf(torrent, picked), sourcesForDownload = true) }
        val blocked = withTimeout(10_000) { app.state.first { it.downloadableSources.isNotEmpty() } }
        assertEquals(mapOf("t" to org.viptv.app.downloads.DOWNLOAD_TORRENT_ONLY, "d" to null), blocked.downloadableSources)
        app.playSource(torrent)
        assertEquals(null, engine.record(downloadId("12", episodes[1])))
        app.playSource(picked)
        val record = engine.record(downloadId("12", episodes[1]))!!
        assertEquals(picked, record.source)
        assertTrue(record.pinned)
        assertEquals(Screen.Details, app.state.value.screen)
        assertTrue(!app.state.value.sourcesForDownload)
    }

    @Test fun changeSourceFromTheListReplacesTheExistingDownload() = runBlocking<Unit> {
        val engine = engine(MemoryFiles(), completed(episodes[0]).copy(source = SourceCandidate("old", "addon", "Old 720p", source_fingerprint = "fp-old")))
        val app = signedIn(engine)
        val id = downloadId("12", episodes[0])
        val picked = SourceCandidate("new", "addon", "New 1080p", source_fingerprint = "fp-new")
        app.updateState { it.copy(screen = Screen.Sources, sourceTitle = episodes[0], sources = listOf(picked), sourcesForDownload = true) }
        app.playSource(picked)
        val record = engine.record(id)!!
        assertEquals(picked, record.source)
        assertEquals("Old 720p", record.previous?.source?.label)
    }

    @Test fun aSeasonFromAManualPickCarriesTheRelease() = runBlocking<Unit> {
        val engine = engine(MemoryFiles())
        val app = signedIn(engine)
        val picked = SourceCandidate("d", "addon", "Show 1080p", binge_group = "group-a")
        app.downloadSeason(Card(series, provider_id = "catalog", title = "Show", badges = emptyList()), 1, picked)
        withTimeout(10_000) { engine.items.first { it.size == 4 } }
        assertTrue(engine.records().all { it.release == picked && !it.pinned })
    }

    private fun progressWrites() = synchronized(requests) {
        requests.filter { it.first.url.encodedPath == "/api/profiles/12/progress" }.map { it.second }
    }

    private fun signedIn(engine: DownloadEngine) = ApplicationController(scope, store, downloads = engine).also {
        it.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        it.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "12")
    }

    private fun completed(card: Card) = DownloadRecord(downloadId("12", card), "12", card, "Show", DownloadState.Completed,
        bytesDone = 10, bytesTotal = 10, url = "https://cdn.test/1", addedAtMillis = 1)

    private fun engine(files: MemoryFiles, vararg records: DownloadRecord): DownloadEngine {
        records.forEach { files.write(it.fileName, ByteArray(it.bytesDone.toInt())) }
        if (records.isNotEmpty()) files.writeBook(Json.encodeToString(DownloadBook.serializer(), DownloadBook(records.toList())))
        return DownloadEngine(files, client, network, scope, retryDelays = listOf(10, 10, 10), saveDelayMillis = 10)
    }
}
