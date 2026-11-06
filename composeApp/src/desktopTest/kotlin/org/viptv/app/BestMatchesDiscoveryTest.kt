package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

private const val RANKING_SPACING_MILLIS = 300L
private const val GRACE_MILLIS = 1_500L
private const val CAP_MILLIS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class BestMatchesDiscoveryTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())
    private fun row(id: String, height: Long, releaseType: String? = null) =
        SourceCandidate(id, "addon:$id", "Source $id", quality_height = height, release_type = releaseType)

    private class Discovery(fixture: LocalSetupFixture, val firstBatch: List<SourceCandidate>, val secondBatch: List<SourceCandidate>,
        private val rankingFails: (Int) -> Boolean = { false }, private val fullyMatches: (Int) -> Boolean = { false }) {
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
                if (rankingFails(rankingCalls.size)) throw LocalFailure("ranking unavailable")
                val ranked = sources.sortedWith(compareBy<SourceCandidate> { it.release_type == "cam" }.thenByDescending { it.quality_height })
                return RankedSources(ranked, interimSourceGroups(ranked), bestMatchFullyMatches = fullyMatches(rankingCalls.size))
            }
            override suspend fun openSource(id: String): PlaybackInput { opened += id; throw LocalFailure("Stop after choosing.") }
        }
    }

    private fun discover(selection: String, discovery: (LocalSetupFixture) -> Discovery,
        check: suspend TestScope.(ApplicationController, Discovery) -> Unit) = runTest {
        val fixture = LocalSetupFixture()
        val fake = discovery(fixture)
        val controller = fixture.controller(backgroundScope) { fake.device }
        controller.local = fake.device
        controller.capability = { DeviceCapability() }
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = selection)))
        try { controller.chooseSource(movie); check(controller, fake) }
        finally { fake.release.complete(Unit); fixture.close(controller) }
    }

    // Discovery ranks its last arrivals, then waits out one ranking spacing per arrival before it returns.
    private fun TestScope.settle(controller: ApplicationController) {
        repeat(5) {
            if (!controller.state.value.findingSources && controller.job?.isActive != true) return
            advanceTimeBy(RANKING_SPACING_MILLIS); runCurrent()
        }
    }

    @Test fun rowsAreRankedAndGroupedAsTheyArriveAndTheBetterRowInsideTheGraceWindowStarts() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("cam", 2160, "cam"), row("b", 1080)),
            listOf(row("c", 480), row("d", 360), row("e", 240), row("best", 2160)), fullyMatches = { call -> call <= 2 })
    }) { controller, fake ->
        runCurrent()
        assertEquals(listOf("b", "a", "cam"), controller.state.value.sources.map { it.id },
            "The first answer is shown ranked, never in arrival order")
        assertEquals(SourceGroups(listOf("b", "a"), "b"), controller.state.value.sourceGroups)
        assertTrue(controller.state.value.findingSources)
        assertTrue(fake.opened.isEmpty(), "The full match waits the grace window before it starts")

        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "b", "a", "c", "d", "e", "cam"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("best", "b", "a", "c", "d"), "best"), controller.state.value.sourceGroups,
            "The better row takes the marker; the sixth passing row and the camera row stay in All sources")
        assertEquals(listOf("best"), fake.opened, "The better row that arrives before discovery ends starts instead of the slower first one")
        assertEquals(2, fake.rankingCalls.size)
    }

    @Test fun alwaysAskGroupsTheSameWayAndPlaysNothing() = discover("ask", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 2160)))
    }) { controller, fake ->
        runCurrent()
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "a"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("best", "a"), "best"), controller.state.value.sourceGroups)
        assertTrue(fake.opened.isEmpty())
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun aFailedLateRankingKeepsTheShownOrderWithoutGroupsAndDoesNotStartAgain() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("b", 1080)), listOf(row("best", 2160)), rankingFails = { call -> call > 1 },
            fullyMatches = { call -> call == 1 })
    }) { controller, fake ->
        runCurrent()
        advanceTimeBy(GRACE_MILLIS); runCurrent()
        assertEquals(listOf("b"), fake.opened, "The full match starts after the grace window, before a later ranking fails")

        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("b", "a", "best"), controller.state.value.sources.map { it.id }, "The late row appends in arrival order")
        assertEquals(SourceGroups(), controller.state.value.sourceGroups)
        assertEquals(listOf("b"), fake.opened, "The failed ranking starts nothing and the started source keeps playing")
    }

    @Test fun aFailedEarlyRankingStartsNothing() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("b", 1080)), emptyList(), rankingFails = { true }, fullyMatches = { true })
    }) { controller, fake ->
        runCurrent()
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("a", "b"), controller.state.value.sources.map { it.id }, "Rows stay in the order already shown")
        assertTrue(fake.opened.isEmpty(), "A failed ranking starts nothing for this discovery")
    }

    @Test fun onlyCameraReleasesFormNoGroupAndAutoplayPlaysNothing() = discover("best", {
        Discovery(it, listOf(row("cam1", 1080, "cam"), row("cam2", 2160, "cam")), emptyList())
    }) { controller, fake ->
        runCurrent()
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(SourceGroups(), controller.state.value.sourceGroups, "No header and no marker (scenario 15)")
        assertEquals(listOf("cam2", "cam1"), controller.state.value.sources.map { it.id },
            "The camera rows are still listed for the viewer to pick")
        assertTrue(fake.opened.isEmpty(), "Best match autoplay plays nothing when every row fails the hard filters (scenario 5)")
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun aSinglePassingRowAutoplaysWhileCameraRowsStayListed() = discover("best", {
        Discovery(it, listOf(row("cam", 1080, "cam"), row("good", 720)), emptyList())
    }) { controller, fake ->
        runCurrent()
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("good", "cam"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("good"), "good"), controller.state.value.sourceGroups)
        assertEquals(listOf("good"), fake.opened, "The one passing row is the autoplay target at the end of discovery")
    }

    @Test fun theFullMatchStartsAfterTheGraceWindowBeforeTheSlowProviderFinishes() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("good", 1080)), listOf(row("best", 2160)), fullyMatches = { call -> call == 1 })
    }) { controller, fake ->
        runCurrent()
        assertTrue(fake.opened.isEmpty())
        advanceTimeBy(GRACE_MILLIS); runCurrent()
        assertFalse(fake.release.isCompleted, "Playback starts while the slow provider is still answering")
        assertEquals(listOf("good"), fake.opened, "The full match starts once the grace window closes")
    }

    @Test fun withoutAFullMatchTheCapStartsTheBestRowAndLaterRowsDoNotReplaceIt() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 1080)))
    }) { controller, fake ->
        runCurrent()
        advanceTimeBy(CAP_MILLIS - 1); runCurrent()
        assertTrue(fake.opened.isEmpty(), "No full match: the best row waits the 5 s cap")
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("a"), fake.opened, "No full match: the best eligible row starts at the cap")

        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "a"), controller.state.value.sources.map { it.id })
        assertEquals(listOf("a"), fake.opened, "A better row arriving after the start neither replaces it nor interrupts playback")
    }

    @Test fun closingTheDrawerDuringDiscoveryPlaysNothing() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 1080)), fullyMatches = { call -> call >= 2 })
    }) { controller, fake ->
        runCurrent()
        controller.backSources()
        fake.release.complete(Unit)
        settle(controller)
        assertTrue(fake.opened.isEmpty(), "Closing the drawer cancels discovery before the start")
        assertNull(controller.state.value.openingSource)
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun choosingASourceDuringDiscoveryPlaysOnlyThatSource() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("good", 1080)), listOf(row("best", 2160)), fullyMatches = { call -> call >= 2 })
    }) { controller, fake ->
        runCurrent()
        val chosen = requireNotNull(controller.state.value.sources.firstOrNull { it.id == "a" })
        controller.playSource(chosen)
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("a"), fake.opened, "The viewer's pick is the only source that opens")
    }

    @Test fun scrollingTheDrawerDuringDiscoveryPlaysNothing() = discover("best", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 1080)), fullyMatches = { call -> call >= 2 })
    }) { controller, fake ->
        runCurrent()
        controller.markSourcesScrolled()
        fake.release.complete(Unit)
        settle(controller)
        assertTrue(fake.opened.isEmpty(), "Dragging the list stops the automatic start for this discovery")
        assertFalse(controller.state.value.findingSources)
    }
}