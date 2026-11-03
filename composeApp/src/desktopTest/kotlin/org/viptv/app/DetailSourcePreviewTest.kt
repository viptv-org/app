package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class DetailSourcePreviewTest {
    private val series = Card(MediaIdentity("show", "series", emptyList()), provider_id = "addon:1", title = "Fixture show", badges = emptyList())
    private fun episode(season: Long, number: Long, progress: Double? = null) = series.copy(
        identity = MediaIdentity("show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number", progress = progress)
    private fun row(id: String, quality: Long = 1080) = SourceCandidate(id, "addon:$id", "Source $id",
        quality_height = quality, presentation = SourcePresentation(provider_name = "Provider $id"))

    private inner class Harness(var rows: (Card) -> List<SourceCandidate> = { listOf(row("a"), row("b", 720)) },
        private val episodes: List<Card> = listOf(episode(1, 1), episode(1, 2, progress = 0.4))) {
        val fixture = LocalSetupFixture()
        val discovered = mutableListOf<Card>()
        val opened = mutableListOf<String>()
        @Volatile var publishAfter: CompletableDeferred<Unit>? = null
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot =
                DetailSnapshot(series, episodes = episodes)
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                discovered += card
                publishAfter?.await()
                val candidates = rows(card)
                publish(SourcesOutcome(candidates))
                return SourcesOutcome(candidates)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources =
                RankedSources(sources, interimSourceGroups(sources))
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
        val controller = ApplicationController(fixture.scope, fixture.store, localFactory = { device }).also { controller ->
            controller.local = device
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                detail = DetailSnapshot(series, episodes = episodes),
                preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "ask")))
        }
        fun close() = fixture.close(controller)
    }

    private suspend fun ApplicationController.settledPreview(): DetailSourcePreview =
        withTimeout(5_000) { state.first { it.detailSourcePreview?.settled == true } }.detailSourcePreview!!

    @Test fun aSeriesTitlePreviewsSourcesForItsRouterChosenEpisode() = runBlocking<Unit> {
        val harness = Harness()
        try {
            harness.controller.previewDetailSources(series)
            harness.controller.settledPreview()
            assertEquals(listOf(episode(1, 1)), harness.discovered, "The preview targets the same episode the Play button starts")
        } finally { harness.close() }
    }

    @Test fun aResumedSeriesPreviewsTheResumeEpisode() = runBlocking<Unit> {
        val harness = Harness()
        try {
            harness.controller.previewDetailSources(episode(1, 2, progress = 0.4))
            harness.controller.settledPreview()
            assertEquals(listOf(episode(1, 2, progress = 0.4)), harness.discovered)
        } finally { harness.close() }
    }

    @Test fun repeatedRecompositionStartsNoSecondDiscovery() = runBlocking<Unit> {
        val harness = Harness()
        try {
            repeat(3) { harness.controller.previewDetailSources(series) }
            harness.controller.settledPreview()
            harness.controller.previewDetailSources(series)
            delay(600)
            assertEquals(1, harness.discovered.size)
            assertTrue(harness.opened.isEmpty(), "A preview never starts playback")
            assertEquals(Screen.Details, harness.controller.state.value.screen)
        } finally { harness.close() }
    }

    @Test fun aSettledPreviewIsAdoptedByThePickerWithoutADiscoveryOrPlayback() = runBlocking<Unit> {
        val harness = Harness()
        val controller = harness.controller
        try {
            controller.previewDetailSources(series)
            val preview = controller.settledPreview()
            assertEquals(listOf("a", "b"), preview.rows.map { it.id })
            controller.chooseSource(series)
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertEquals(listOf("a", "b"), controller.state.value.sources.map { it.id })
            assertFalse(controller.state.value.findingSources)
            assertEquals(1, harness.discovered.size, "The picker adopts the finished preview instead of discovering again")
            assertTrue(harness.opened.isEmpty(), "Opening the summary never starts a source")
        } finally { harness.close() }
    }

    @Test fun aRunningPreviewKeepsFeedingTheOpenPicker() = runBlocking<Unit> {
        val harness = Harness()
        val controller = harness.controller
        try {
            val release = CompletableDeferred<Unit>()
            harness.publishAfter = release
            controller.previewDetailSources(series)
            delay(700)
            assertEquals(emptyList(), controller.state.value.detailSourcePreview?.rows, "No rows have arrived yet")
            controller.chooseSource(series)
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertTrue(controller.state.value.findingSources, "The picker shows the adopted discovery still running")
            release.complete(Unit)
            val shown = withTimeout(5_000) { controller.state.first { !it.findingSources && it.sources.isNotEmpty() } }
            assertEquals(listOf("a", "b"), shown.sources.map { it.id })
            assertEquals(1, harness.discovered.size, "The running preview is the picker's only discovery")
        } finally { harness.close() }
    }

    @Test fun anEmptyPreviewRecoversThroughAFreshPickerAttempt() = runBlocking<Unit> {
        val harness = Harness(rows = { emptyList() })
        val controller = harness.controller
        try {
            controller.previewDetailSources(series)
            controller.settledPreview()
            controller.chooseSource(series)
            withTimeout(5_000) { controller.state.first { !it.findingSources } }
            assertEquals(2, harness.discovered.size, "An empty preview leaves the picker its own attempt")
            assertEquals(Screen.Sources, controller.state.value.screen)
        } finally { harness.close() }
    }

    @Test fun aFailedPreviewIsAdoptedOnlyWhenItRetainedRows() = runBlocking<Unit> {
        val harness = Harness(rows = { throw LocalFailure("Providers did not answer.") })
        val controller = harness.controller
        try {
            controller.previewDetailSources(series)
            val preview = controller.settledPreview()
            assertTrue(preview.failed)
            assertTrue(preview.rows.isEmpty())
            controller.chooseSource(series)
            withTimeout(5_000) { controller.state.first { !it.findingSources } }
            assertEquals(2, harness.discovered.size, "A failure without rows is retried in the picker")
        } finally { harness.close() }
    }

    @Test fun leavingTheTitleCancelsThePreviewAndLateRowsCannotLand() = runBlocking<Unit> {
        val harness = Harness()
        val controller = harness.controller
        try {
            val release = CompletableDeferred<Unit>()
            harness.publishAfter = release
            controller.previewDetailSources(series)
            delay(700)
            controller.cancelDetailSourcePreview()
            assertNull(controller.state.value.detailSourcePreview)
            release.complete(Unit)
            delay(600)
            assertNull(controller.state.value.detailSourcePreview, "A cancelled preview publishes nothing")
            assertTrue(controller.state.value.sources.isEmpty())
        } finally { harness.close() }
    }

    @Test fun aSeriesWithoutAnAvailableEpisodeDiscoversNothing() = runBlocking<Unit> {
        val harness = Harness(episodes = emptyList())
        try {
            harness.controller.previewDetailSources(series)
            delay(800)
            assertTrue(harness.discovered.isEmpty())
            assertNull(harness.controller.state.value.detailSourcePreview)
        } finally { harness.close() }
    }
}