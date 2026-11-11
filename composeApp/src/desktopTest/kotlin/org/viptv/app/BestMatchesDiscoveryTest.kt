package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class BestMatchesDiscoveryTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())
    private fun row(id: String, height: Long, releaseType: String? = null) =
        SourceCandidate(id, "addon:$id", "Source $id", quality_height = height, release_type = releaseType)

    private class Discovery(fixture: LocalSetupFixture, val firstBatch: List<SourceCandidate>, val secondBatch: List<SourceCandidate>,
        private val rankingFails: Boolean = false, private val fullyMatches: (Int) -> Boolean = { false }) {
        val firstRanked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val rankingCalls = mutableListOf<List<String>>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                publish(SourcesOutcome(firstBatch))
                release.await()
                publish(SourcesOutcome(firstBatch + secondBatch))
                return SourcesOutcome(firstBatch + secondBatch)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources {
                rankingCalls += sources.map { it.id }
                if (rankingFails && rankingCalls.size > 1) throw LocalFailure("ranking unavailable")
                val ranked = sources.sortedWith(compareBy<SourceCandidate> { it.release_type == "cam" }.thenByDescending { it.quality_height })
                return RankedSources(ranked, interimSourceGroups(ranked), bestMatchFullyMatches = fullyMatches(rankingCalls.size))
                    .also { if (rankingCalls.size == 1) firstRanked.complete(Unit) }
            }
            override suspend fun openSource(id: String): PlaybackInput { opened += id; return PlaybackInput(id, "https://media.example/$id", profileId = "default") }
        }
    }

    private fun discover(selection: String, discovery: (LocalSetupFixture) -> Discovery, manual: Boolean = false, check: suspend (ApplicationController, Discovery) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val fake = discovery(fixture)
        val controller = fixture.controller { fake.device }; controller.local = fake.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true, detail = DetailSnapshot(movie, episodes = emptyList()),
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = selection)))
        try { if (manual) controller.chooseSource(movie) else controller.playTitle(movie); check(controller, fake) }
        finally { fake.release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    private suspend fun settle(controller: ApplicationController) {
        while (true) { val job = controller.job ?: return; job.join(); if (controller.job === job) return }
    }

    @Test fun rowsRankProgressivelyButAnImperfectTargetWaitsForDiscoveryCompletion() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("cam", 2160, "cam"), row("b", 1080)),
            listOf(row("c", 480), row("d", 360), row("e", 240), row("best", 2160)))
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        val early = withTimeout(5000) { controller.state.first { it.sources.isNotEmpty() } }
        assertEquals(listOf("b", "a", "cam"), early.sources.map { it.id })
        assertEquals(SourceGroups(listOf("b", "a"), "b"), early.sourceGroups)
        assertTrue(fake.opened.isEmpty())
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "b", "a", "c", "d", "e", "cam"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("best", "b", "a", "c", "d"), "best"), controller.state.value.sourceGroups)
        assertEquals(listOf("best"), fake.opened)
    }

    @Test fun explicitChooseSourceAlwaysAsksEvenWhenCoreReportsAReadyBestMatch() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 2160)), fullyMatches = { true })
    }, manual = true) { controller, fake ->
        fake.release.complete(Unit); settle(controller)
        assertTrue(fake.opened.isEmpty())
        assertEquals(Screen.Sources, controller.state.value.screen)
    }

    @Test fun alwaysAskNeverStartsAReadyBestMatch() = discover("ask", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 2160)), fullyMatches = { true })
    }) { controller, fake ->
        fake.release.complete(Unit); settle(controller)
        assertEquals(listOf("best", "a"), controller.state.value.sources.map { it.id })
        assertTrue(fake.opened.isEmpty())
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun aFailedRankingPreservesAvailableRowsAndPreventsAutomaticPlayback() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("b", 1080)), listOf(row("best", 2160)), rankingFails = true)
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        fake.release.complete(Unit); settle(controller)
        assertEquals(listOf("b", "a", "best"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(), controller.state.value.sourceGroups)
        assertTrue(fake.opened.isEmpty())
    }

    @Test fun hardFilteredRowsAreListedWithoutAnAutomaticTarget() = discover("best", {
        Discovery(it, listOf(row("cam1", 1080, "cam"), row("cam2", 2160, "cam")), emptyList(), fullyMatches = { true })
    }) { controller, fake ->
        fake.release.complete(Unit); settle(controller)
        assertEquals(SourceGroups(), controller.state.value.sourceGroups)
        assertEquals(listOf("cam2", "cam1"), controller.state.value.sources.map { it.id })
        assertTrue(fake.opened.isEmpty())
    }

    @Test fun theFirstCoreReadyTargetStartsWithoutWaitingForTheSlowProvider() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("good", 1080)), listOf(row("later", 2160)), fullyMatches = { true })
    }) { controller, fake ->
        withTimeout(5000) { controller.state.first { it.playback != null } }
        assertFalse(fake.release.isCompleted)
        assertEquals(listOf("good"), fake.opened)
        fake.release.complete(Unit); settle(controller)
        assertEquals(listOf("good"), fake.opened)
    }

    @Test fun closingBeforeReadinessPreventsTheAutomaticStart() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 1080)), fullyMatches = { it >= 2 })
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        assertTrue(fake.opened.isEmpty())
        controller.backSources(); fake.release.complete(Unit); settle(controller)
        assertTrue(fake.opened.isEmpty())
        assertNull(controller.state.value.openingSource)
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun manualSelectionBeforeReadinessPlaysOnlyTheChosenSource() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("good", 1080)), listOf(row("best", 2160)), fullyMatches = { it >= 2 })
    }) { controller, fake ->
        val chosen = withTimeout(5000) { controller.state.first { it.sources.isNotEmpty() } }.sources.first { it.id == "a" }
        controller.playSource(chosen); fake.release.complete(Unit); settle(controller)
        assertEquals(listOf("a"), fake.opened)
    }

    @Test fun scrollingBeforeReadinessKeepsTheUpdatedPickerWithoutStartingPlayback() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 1080)), fullyMatches = { it >= 2 })
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        controller.markSourcesScrolled(); fake.release.complete(Unit); settle(controller)
        assertEquals(listOf("best", "a"), controller.state.value.sources.map { it.id })
        assertTrue(fake.opened.isEmpty())
        assertFalse(controller.state.value.findingSources)
    }
}
