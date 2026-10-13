package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ClosingProgressTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    @Test fun disconnectSavesCapturedProfileEvenWhenUiScopeWasCancelled() = runBlocking {
        val saved = CompletableDeferred<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v3/profiles/12/progress", request.url.encodedPath)
            saved.complete(Json.parseToJsonElement((request.body as TextContent).text).jsonObject)
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val owner = SupervisorJob()
        val controller = ApplicationController(CoroutineScope(owner + Dispatchers.Default), Store())
        controller.connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val card = Card(identity = MediaIdentity("opaque-movie", "movie", emptyList()), provider_id = "provider", title = "Movie", badges = emptyList())
        controller.mutable.value = ApplicationState(selectedProfile = "12", sourceTitle = card,
            playback = PlaybackInput("lease", "https://media.example/movie", profileId = "12"))
        controller.lastProgress = PlayerFact.Progress(12_345, 60_000)
        owner.cancel(); controller.disconnect()
        val value = withTimeout(2_000) { saved.await() }
        val record = Json.decodeFromJsonElement<PlaybackRecord>(value.getValue("record"))
        assertEquals(12_345L, record.position_millis)
        assertEquals(card.identity, record.identity)
        assertNull(controller.state.value.selectedProfile)
    }
}
