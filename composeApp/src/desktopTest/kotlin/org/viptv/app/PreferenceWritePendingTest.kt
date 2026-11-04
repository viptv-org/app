package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class PreferenceWritePendingTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val saved = ProfilePreferences(next_episode_button = false, skip_intros = false)

    private fun snapshot(preferences: ProfilePreferences, revision: Long = 4) =
        Json.encodeToString(PreferencesSnapshot(revision, preferences))

    private inner class Fixture(scope: CoroutineScope, scheduler: TestCoroutineScheduler, reply: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val writes = mutableListOf<ProfilePreferences>()
        val client = HttpClient(MockEngine) {
            engine { dispatcher = StandardTestDispatcher(scheduler); addHandler(reply) }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false, workerDispatcher = StandardTestDispatcher(scheduler))
        val controller = ApplicationController(scope, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1",
                preferences = PreferencesSnapshot(4, saved))
        }
        fun close() { controller.mutationJobs.values.toList().forEach { it.cancel() }; remote.close() }
    }

    @Test fun aToggledRowFlipsInTheFrameDropsTheRepeatAndKeepsTheHostsAnswer() = runTest {
        val release = CompletableDeferred<Unit>()
        val writes = mutableListOf<ProfilePreferences>()
        val f = Fixture(this, testScheduler) { request ->
            writes += Json.decodeFromString<ProfilePreferences>(
                Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject.getValue("preferences").toString())
            release.await()
            respond(snapshot(saved.copy(next_episode_button = true), revision = 5), headers = json)
        }
        try {
            val target = preferenceTarget("Next episode button")
            f.controller.updatePreferences(saved.copy(next_episode_button = true), "Next episode button")
            runCurrent()
            assertTrue(f.controller.state.value.preferences?.preferences?.next_episode_button == true,
                "the row flips in the frame of the press")
            assertTrue(f.controller.state.value.mutations.isPending(target))

            f.controller.updatePreferences(saved.copy(next_episode_button = true), "Next episode button")
            runCurrent()
            assertEquals(1, writes.size, "a repeat press on the pending row is dropped")

            release.complete(Unit)
            advanceUntilIdle()
            assertTrue(f.controller.state.value.preferences?.preferences?.next_episode_button == true)
            assertEquals(5, f.controller.state.value.preferences?.revision)
            assertFalse(f.controller.state.value.mutations.isPending(target))
        } finally { release.complete(Unit); f.close() }
    }

    @Test fun aRefusedWritePutsTheLastGoodValueBackAndRetriesFromTheRow() = runTest {
        var puts = 0
        val f = Fixture(this, testScheduler) { _ ->
            puts++
            if (puts == 1) respond("nope", HttpStatusCode.InternalServerError)
            else respond(snapshot(saved.copy(skip_intros = true), revision = 5), headers = json)
        }
        try {
            val target = preferenceTarget("Skip intros")
            f.controller.updatePreferences(saved.copy(skip_intros = true), "Skip intros")
            advanceUntilIdle()
            assertFalse(f.controller.state.value.preferences?.preferences?.skip_intros == true, "the refused value is reverted")
            assertEquals(4, f.controller.state.value.preferences?.revision)
            assertNotNull(f.controller.state.value.preferencesError)
            assertNotNull(f.controller.state.value.mutations.failure(target))

            f.controller.retryMutation(target)
            advanceUntilIdle()
            assertEquals(2, puts)
            assertTrue(f.controller.state.value.preferences?.preferences?.skip_intros == true)
            assertNull(f.controller.state.value.mutations.failure(target))
        } finally { f.close() }
    }

    @Test fun aRejectedChoiceNeverFlipsTheRowAndShowsWhy() = runTest {
        var puts = 0
        val f = Fixture(this, testScheduler) { _ -> puts++; respond(snapshot(saved), headers = json) }
        try {
            val target = preferenceTarget("Household PIN")
            f.controller.mutable.value = f.controller.state.value.copy(parentSettings = null)
            f.controller.changeParentPin("", "1234")
            advanceUntilIdle()
            assertEquals(0, puts, "a rejected press sends nothing")
            assertEquals("Refresh parental controls first.", f.controller.state.value.mutations.failure(target))
        } finally { f.close() }
    }
}
