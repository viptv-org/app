package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class DesktopCloseTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private val card = Card(identity = MediaIdentity("opaque-movie", "movie", emptyList()), provider_id = "provider",
        title = "Movie", badges = emptyList())

    private fun controller(remote: RemoteConnection): ApplicationController {
        val controller = ApplicationController(CoroutineScope(SupervisorJob() + Dispatchers.Default), Store())
        controller.connection = remote
        val input = PlaybackInput("lease", "https://media.example/movie", profileId = "12")
        controller.mutable.value = ApplicationState(selectedProfile = "12", sourceTitle = card, playback = input)
        controller.lastProgress = PlayerFact.Progress(12_345, 60_000)
        controller.remotePlayback = RemotePlaybackLease(input, 20, remote)
        return controller
    }

    @Test fun closeSavesProgressAndReleasesLeaseBeforeExit() = runBlocking {
        val events = EngineRequests<String>()
        val progress = CompletableDeferred<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/profiles/12/progress" && request.method == HttpMethod.Put -> {
                    events += "progress"
                    progress.complete(Json.parseToJsonElement((request.body as TextContent).text).jsonObject)
                    respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                request.url.encodedPath == "/api/playback/lease" && request.method == HttpMethod.Delete -> {
                    events += "delete"
                    respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val controller = controller(remote)
        controller.closeForExit(3_000) { events += "exit" }
        assertEquals(listOf("progress", "delete", "exit"), events.toList())
        val record = Json.decodeFromJsonElement<PlaybackRecord>(progress.await().getValue("record"))
        assertEquals(12_345L, record.position_millis)
    }

    @Test fun closeStillExitsWhenTheServerNeverAnswers() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/profiles/12/progress" -> awaitCancellation()
                request.url.encodedPath == "/api/playback/lease" -> awaitCancellation()
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val controller = controller(remote)
        val started = TimeSource.Monotonic.markNow()
        var exited = false
        controller.closeForExit(300) { exited = true }
        assertTrue(exited)
        assertTrue(started.elapsedNow() >= 250.milliseconds)
    }
}