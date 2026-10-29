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
        private val rankingFails: Boolean = false) {
        val firstRanked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val rankingCalls = mutableListOf<List<String>>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit): SourcesOutcome {
                publish(firstBatch)
                release.await()
                publish(firstBatch + secondBatch)
                return SourcesOutcome(firstBatch + secondBatch)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, deviceMaxHeight: Int?): RankedSources {
                rankingCalls += sources.map { it.id }
                if (rankingFails && rankingCalls.size > 1) throw LocalFailure("ranking unavailable")
                val ranked = sources.sortedWith(compareBy<SourceCandidate> { it.release_type == "cam" }.thenByDescending { it.quality_height })
                return RankedSources(ranked, interimSourceGroups(ranked)).also { if (rankingCalls.size == 1) firstRanked.complete(Unit) }
            }
            override suspend fun openSource(id: String): PlaybackInput { opened += id; throw LocalFailure("Stop after choosing.") }
        }
    }

    private fun discover(selection: String, discovery: (LocalSetupFixture) -> Discovery, check: suspend (ApplicationController, Discovery) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val fake = discovery(fixture)
        val controller = fixture.controller { fake.device }; controller.local = fake.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = selection)))
        try { controller.chooseSource(movie); check(controller, fake) }
        finally { fake.release.complete(Unit); controller.job?.cancelAndJoin(); fixture.close(controller) }
    }

    private suspend fun settle(controller: ApplicationController) {
        while (true) { val job = controller.job ?: return; job.join(); if (controller.job === job) return }
    }

    @Test fun rowsAreRankedAndGroupedAsTheyArriveAndAutoplayTargetsTheFirstBestMatch() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("cam", 2160, "cam"), row("b", 1080)),
            listOf(row("c", 480), row("d", 360), row("e", 240), row("best", 2160)))
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        val early = withTimeout(5000) { controller.state.first { it.sources.isNotEmpty() } }
        assertEquals(listOf("b", "a", "cam"), early.sources.map { it.id }, "The first answer is shown ranked, never in arrival order")
        assertEquals(SourceGroups(listOf("b", "a"), "b"), early.sourceGroups)
        assertTrue(early.findingSources)
        assertTrue(fake.opened.isEmpty(), "Nothing autoplays while providers are still answering")

        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "b", "a", "c", "d", "e", "cam"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("best", "b", "a", "c", "d"), "best"), controller.state.value.sourceGroups,
            "The better row takes the marker; the sixth passing row and the camera row stay in All sources")
        assertEquals(listOf("best"), fake.opened, "Best match autoplay still plays the first Best matches row")
        assertEquals(2, fake.rankingCalls.size)
    }

    @Test fun alwaysAskGroupsTheSameWayAndPlaysNothing() = discover("ask", {
        Discovery(it, listOf(row("a", 720)), listOf(row("best", 2160)))
    }) { controller, fake ->
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("best", "a"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("best", "a"), "best"), controller.state.value.sourceGroups)
        assertTrue(fake.opened.isEmpty())
        assertFalse(controller.state.value.findingSources)
    }

    @Test fun aFailedRankingKeepsTheShownOrderWithoutGroupsAndDoesNotAutoplay() = discover("best", {
        Discovery(it, listOf(row("a", 720), row("b", 1080)), listOf(row("best", 2160)), rankingFails = true)
    }) { controller, fake ->
        withTimeout(5000) { fake.firstRanked.await() }
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("b", "a", "best"), controller.state.value.sources.map { it.id }, "The late row appends in arrival order")
        assertEquals(SourceGroups(), controller.state.value.sourceGroups)
        assertTrue(fake.opened.isEmpty(), "A failed ranking leaves the picker as on Always ask")
        assertNull(controller.state.value.error)
    }

    @Test fun onlyCameraReleasesFormNoGroupAndAutoplayPlaysNothing() = discover("best", {
        Discovery(it, listOf(row("cam1", 1080, "cam"), row("cam2", 2160, "cam")), emptyList())
    }) { controller, fake ->
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
        fake.release.complete(Unit)
        settle(controller)
        assertEquals(listOf("good", "cam"), controller.state.value.sources.map { it.id })
        assertEquals(SourceGroups(listOf("good"), "good"), controller.state.value.sourceGroups)
        assertEquals(listOf("good"), fake.opened, "The one passing row is the autoplay target, unchanged")
    }
}
