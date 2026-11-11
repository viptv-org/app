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
        val openGate: CompletableDeferred<Unit>? = null, val ready: (List<SourceCandidate>) -> Boolean = { true }) {
        val firstRanked = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                try {
                    publish(SourcesOutcome(first))
                    gate.await()
                    publish(SourcesOutcome(first + second))
                    return SourcesOutcome(first + second)
                } finally { if (!gate.isCompleted) cancelled.complete(Unit) }
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources {
                val ranked = sources.sortedByDescending { it.quality_height }
                firstRanked.complete(Unit)
                return RankedSources(ranked, interimSourceGroups(ranked), bestMatchFullyMatches = ready(sources))
            }
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                openGate?.await()
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
    }

    private fun runDiscovery(first: List<SourceCandidate>, second: List<SourceCandidate>, openGate: CompletableDeferred<Unit>? = null,
        ready: (List<SourceCandidate>) -> Boolean = { true }, check: suspend (ApplicationController, Streaming) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val streaming = Streaming(fixture, first, second, openGate, ready)
        val controller = fixture.controller { streaming.device }; controller.local = streaming.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true, detail = DetailSnapshot(movie, episodes = emptyList()),
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "best")))
        try { controller.playTitle(movie); check(controller, streaming) }
        finally { streaming.openGate?.complete(Unit); streaming.gate.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    @Test fun aCoreReadyTargetStartsOnceAndCancelsTheRemainingDiscovery() = runDiscovery(
        listOf(row("early", 1080)), listOf(row("late", 2160)), openGate = CompletableDeferred()) { controller, streaming ->
        withTimeout(5000) { while (streaming.opened.isEmpty()) delay(5) }
        withTimeout(5000) { streaming.cancelled.await() }
        assertFalse(streaming.gate.isCompleted)
        assertEquals(listOf("early"), streaming.opened)
        assertNull(controller.state.value.playback)
        streaming.gate.complete(Unit)
        streaming.openGate?.complete(Unit)
        withTimeout(5000) { controller.state.first { it.playback != null } }
        assertEquals(listOf("early"), streaming.opened)
        assertEquals("early", controller.state.value.playingSource?.id)
    }

    @Test fun anImperfectEarlyRowWaitsAndOnlyTheCoreReadyTargetOpens() = runDiscovery(
        listOf(row("low", 720)), listOf(row("high", 2160)), openGate = CompletableDeferred(),
        ready = { rows -> rows.any { it.id == "high" } }) { controller, streaming ->
        withTimeout(5000) { streaming.firstRanked.await() }
        assertTrue(streaming.opened.isEmpty())
        streaming.gate.complete(Unit)
        withTimeout(5000) { while (streaming.opened.isEmpty()) delay(5) }
        assertEquals(listOf("high"), streaming.opened)
        streaming.openGate?.complete(Unit)
        withTimeout(5000) { controller.state.first { it.playback != null } }
        assertEquals("high", controller.state.value.playingSource?.id)
    }
}
