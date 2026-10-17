package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/** Real Rust/SQLite/UniFFI grants and native adapter; only external provider IO is replaced. */
class NativeProgressiveSourcesTest {
    private val card = Card(MediaIdentity("tt1234567", "movie", emptyList()), provider_id = "metadata", title = "Fixture film", badges = emptyList())
    private class Fixture : AutoCloseable {
        val lateStarted = CompletableDeferred<Unit>()
        val releaseLate = CompletableDeferred<Unit>()
        val lateCancelled = CompletableDeferred<Unit>()
        val directory = Files.createTempDirectory("viptv-native-source-progress-")
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "late.example") {
                lateStarted.complete(Unit)
                try { releaseLate.await() } catch (cancelled: CancellationException) { lateCancelled.complete(Unit); throw cancelled }
            }
            respond("""{"streams":[{"name":"${request.url.host} 1080p","title":"Film.1080p.AVC.AAC.mkv","url":"https://media.example/${request.url.host}.mp4"}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)
        val providers = listOf("fast.example", "late.example").map { host ->
            val installed = local.engine.installAddon("default", "https://$host/manifest.json",
                """{"id":"$host","name":"$host","version":"1","resources":["stream"],"types":["movie"]}""")
            Json.parseToJsonElement(installed).jsonObject.getValue("id").jsonPrimitive.content
        }
        override fun close() { releaseLate.complete(Unit); local.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun firstProviderPublishesUsableStableGrantWhileAnotherProviderIsPending() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = CompletableDeferred<List<SourceCandidate>>()
            val updates = mutableListOf<List<String>>()
            val loading = async { fixture.local.fetchLocalSources("default", card) {
                updates += it.map { source -> source.id }; first.complete(it)
            } }
            try {
                val partial = withTimeout(3_000) { first.await() }
                fixture.lateStarted.await(); assertFalse(loading.isCompleted)
                assertEquals(1, partial.size); assertEquals(1080L, partial.single().quality_height)
                assertTrue(fixture.local.engine.openSource(partial.single().id).contains("fast.example.mp4"))
                fixture.releaseLate.complete(Unit)
                val complete = loading.await(); assertEquals(2, complete.size)
                assertEquals(partial.single().id, complete.first().id)
                assertEquals(listOf(1, 2), updates.map { it.size })
                assertTrue(fixture.local.engine.openSource(partial.single().id).contains("fast.example.mp4"))
            } finally { loading.cancelAndJoin() }
        }
    }

    @Test fun cancellationStopsLateEmissionAndKeepsAlreadySelectedGrantOpen() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = CompletableDeferred<SourceCandidate>(); val updates = mutableListOf<List<String>>()
            val loading = launch { fixture.local.fetchLocalSources("default", card) { updates += it.map { s -> s.id }; first.complete(it.first()) } }
            val selected = withTimeout(3_000) { first.await() }; fixture.lateStarted.await()
            loading.cancelAndJoin(); fixture.releaseLate.complete(Unit)
            assertEquals(1, updates.size); assertTrue(fixture.lateCancelled.isCompleted)
            assertTrue(fixture.local.engine.openSource(selected.id).contains("fast.example.mp4"))
        }
    }

    @Test fun integrationRevisionChangeRejectsLateResultsAndEarlierGrants() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = CompletableDeferred<SourceCandidate>(); val updates = mutableListOf<List<String>>()
            val loading = async { runCatching { fixture.local.fetchLocalSources("default", card) { updates += it.map { s -> s.id }; first.complete(it.first()) } } }
            val selected = withTimeout(3_000) { first.await() }; fixture.lateStarted.await()
            fixture.local.engine.setAddonEnabled("default", fixture.providers.last(), false)
            fixture.releaseLate.complete(Unit)
            assertTrue(loading.await().isFailure); assertEquals(1, updates.size)
            assertFails { fixture.local.engine.openSource(selected.id) }
        }
    }

    @Test fun controllerShowsNativePartialSourcesAndBackPreventsLateScreenMutation() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = ApplicationController(this)
            controller.local = fixture.local
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                detail = DetailSnapshot(card, episodes = emptyList()), preferences = PreferencesSnapshot())
            controller.chooseSource(card)
            try {
                withTimeout(3_000) { while (controller.state.value.sources.isEmpty()) delay(5) }
                fixture.lateStarted.await(); val selected = controller.state.value.sources.single()
                assertTrue(controller.state.value.findingSources); assertEquals(Screen.Sources, controller.state.value.screen)
                controller.backSources(); controller.job?.join(); fixture.releaseLate.complete(Unit)
                assertEquals(Screen.Details, controller.state.value.screen)
                assertFalse(controller.state.value.findingSources); assertEquals(listOf(selected.id), controller.state.value.sources.map { it.id })
                assertTrue(fixture.local.engine.openSource(selected.id).contains("fast.example.mp4"))
            } finally { controller.job?.cancelAndJoin() }
        }
    }
    @Test fun controllerCanSelectEarlyGrantAndCancelRemainingDiscoveryBeforePlayerHandoff() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = ApplicationController(this)
            controller.local = fixture.local
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                detail = DetailSnapshot(card, episodes = emptyList()), preferences = PreferencesSnapshot())
            controller.chooseSource(card)
            try {
                withTimeout(3_000) { while (controller.state.value.sources.isEmpty()) delay(5) }
                fixture.lateStarted.await(); val selected = controller.state.value.sources.single()
                controller.playSource(selected)
                controller.job!!.join()
                assertEquals(Screen.Player, controller.state.value.screen)
                assertEquals(selected.id, controller.state.value.playingSource?.id)
                assertContains(requireNotNull(controller.state.value.playback).uri, "fast.example.mp4")
                assertFalse(controller.state.value.findingSources); assertTrue(fixture.lateCancelled.isCompleted)
                fixture.releaseLate.complete(Unit)
                assertEquals(Screen.Player, controller.state.value.screen)
                assertEquals(listOf(selected.id), controller.state.value.sources.map { it.id })
            } finally { controller.job?.cancelAndJoin(); controller.subtitleDiscovery?.cancelAndJoin() }
        }
    }

    @Test fun controllerRejectsPreviousTitleClickAndResolvesCurrentCandidateById() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val controller = ApplicationController(this)
            controller.local = fixture.local
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                detail = DetailSnapshot(card, episodes = emptyList()), preferences = PreferencesSnapshot())
            controller.chooseSource(card)
            try {
                withTimeout(3_000) { while (controller.state.value.sources.isEmpty()) delay(5) }
                val previous = controller.state.value.sources.single()
                controller.backSources(); controller.job?.join()
                val replacement = card.copy(identity = card.identity.copy(id = "tt7654321"), title = "Replacement film")
                controller.chooseSource(replacement)
                withTimeout(3_000) { while (controller.state.value.sources.isEmpty()) delay(5) }
                val current = controller.state.value.sources.single()
                assertNotEquals(previous.id, current.id)
                assertTrue(fixture.local.engine.openSource(previous.id).contains("fast.example.mp4"))
                controller.playSource(previous)
                assertEquals(Screen.Sources, controller.state.value.screen); assertNull(controller.state.value.playback)
                assertEquals(replacement, controller.state.value.sourceTitle)
                assertEquals(listOf(current.id), controller.state.value.sources.map { it.id })
                controller.playSource(current.copy(label = "Untrusted old label", provider_id = "wrong-origin"))
                controller.job!!.join()
                assertEquals(Screen.Player, controller.state.value.screen)
                assertEquals(current, controller.state.value.playingSource)
                assertEquals(replacement, controller.state.value.sourceTitle)
            } finally { controller.job?.cancelAndJoin(); controller.subtitleDiscovery?.cancelAndJoin() }
        }
    }

}
