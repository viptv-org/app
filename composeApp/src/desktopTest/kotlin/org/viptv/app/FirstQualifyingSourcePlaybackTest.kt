package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class FirstQualifyingSourcePlaybackTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())
    private fun row(id: String, height: Long) = SourceCandidate(id, "addon:$id", "Source $id", quality_height = height)

    private class Streaming(fixture: LocalSetupFixture, val first: List<SourceCandidate>, val second: List<SourceCandidate>,
        val openGate: CompletableDeferred<Unit>? = null) {
        val gate = CompletableDeferred<Unit>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                publish(SourcesOutcome(first))
                gate.await()
                publish(SourcesOutcome(first + second))
                return SourcesOutcome(first + second)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources {
                val ranked = sources.sortedByDescending { it.quality_height }
                return RankedSources(ranked, interimSourceGroups(ranked))
            }
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                openGate?.await()
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
    }

    private fun runDiscovery(first: List<SourceCandidate>, second: List<SourceCandidate>, openGate: CompletableDeferred<Unit>? = null,
        check: suspend (ApplicationController, Streaming) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val streaming = Streaming(fixture, first, second, openGate)
        val controller = fixture.controller { streaming.device }; controller.local = streaming.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true, detail = DetailSnapshot(movie, episodes = emptyList()),
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "best")))
        try { controller.playTitle(movie); check(controller, streaming) }
        finally { streaming.openGate?.complete(Unit); streaming.gate.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun playbackOpensOnTheFirstQualifyingSourceBeforeDiscoveryCompletes() = runDiscovery(
        listOf(row("early", 1080)), listOf(row("late", 2160))) { controller, streaming ->
        withTimeout(5000) { controller.state.first { it.playback != null && it.playingSource?.id == "early" } }
        assertFalse(streaming.gate.isCompleted, "The slow provider has not answered yet")
        assertTrue(controller.state.value.findingSources, "Discovery keeps running while the first source opens")
        assertEquals("early", controller.state.value.playingSource?.id)

        streaming.gate.complete(Unit)
        withTimeout(5000) { controller.state.first { !it.findingSources } }
        assertEquals(listOf("late", "early"), controller.state.value.sources.map { it.id })
        assertEquals("early", controller.state.value.playingSource?.id, "Playback that already began is not replaced")
    }

    @Test fun aClearlyBetterSourceWithinTheGraceWindowSwitchesBeforePlaybackBegins() = runDiscovery(
        listOf(row("low", 720)), listOf(row("high", 2160)), openGate = CompletableDeferred()) { controller, streaming ->
        withTimeout(5000) { while (streaming.opened.isEmpty()) delay(5) }
        assertEquals(listOf("low"), streaming.opened, "The first qualifying source starts opening at once")
        assertNull(controller.state.value.playback, "The held-open source has not committed playback yet")

        streaming.gate.complete(Unit)
        withTimeout(5000) { while (streaming.opened.size < 2) delay(5) }
        assertEquals(listOf("low", "high"), streaming.opened, "The clearly better source supersedes within the grace window")

        streaming.openGate?.complete(Unit)
        withTimeout(5000) { controller.state.first { it.playback != null } }
        assertEquals("high", controller.state.value.playingSource?.id, "The clearly better source is the one that plays")
    }
}