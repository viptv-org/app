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
        val fullyMatches: Boolean = true) {
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
                return RankedSources(ranked, interimSourceGroups(ranked), bestMatchFullyMatches = fullyMatches)
            }
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
    }

    private fun runDiscovery(first: List<SourceCandidate>, second: List<SourceCandidate>, fullyMatches: Boolean = true,
        check: suspend (ApplicationController, Streaming) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val streaming = Streaming(fixture, first, second, fullyMatches)
        val controller = fixture.controller(graceMillis = 200, capMillis = 200) { streaming.device }; controller.local = streaming.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "best")))
        try { controller.chooseSource(movie); check(controller, streaming) }
        finally { streaming.gate.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun playbackOpensOnTheFullMatchBeforeDiscoveryCompletes() = runDiscovery(
        listOf(row("early", 1080)), listOf(row("late", 2160))) { controller, streaming ->
        withTimeout(5000) { controller.state.first { it.playback != null && it.playingSource?.id == "early" } }
        assertFalse(streaming.gate.isCompleted, "The slow provider has not answered yet")
        assertTrue(controller.state.value.findingSources, "Discovery keeps running while the source opens")
        assertEquals("early", controller.state.value.playingSource?.id)

        streaming.gate.complete(Unit)
        withTimeout(5000) { controller.state.first { !it.findingSources } }
        assertEquals(listOf("late", "early"), controller.state.value.sources.map { it.id })
        assertEquals("early", controller.state.value.playingSource?.id, "Playback that already began is not replaced")
        assertEquals(listOf("early"), streaming.opened)
    }

    @Test fun aBetterRowArrivingAfterTheStartDoesNotReplacePlayback() = runDiscovery(
        listOf(row("low", 1080)), listOf(row("high", 2160))) { controller, streaming ->
        withTimeout(5000) { controller.state.first { it.playback != null } }
        assertEquals("low", controller.state.value.playingSource?.id)

        streaming.gate.complete(Unit)
        withTimeout(5000) { controller.state.first { !it.findingSources } }
        assertEquals(listOf("high", "low"), controller.state.value.sources.map { it.id })
        assertEquals("low", controller.state.value.playingSource?.id, "A row that arrives later neither replaces it nor interrupts playback")
        assertEquals(listOf("low"), streaming.opened)
    }

    @Test fun withoutAFullMatchTheCapStartsTheBestRowBeforeDiscoveryCompletes() = runDiscovery(
        listOf(row("early", 1080)), listOf(row("late", 2160)), fullyMatches = false) { controller, streaming ->
        withTimeout(5000) { controller.state.first { it.playback != null && it.playingSource?.id == "early" } }
        assertFalse(streaming.gate.isCompleted, "The cap does not wait for the slow provider")
        assertEquals(listOf("early"), streaming.opened)
    }
}
