package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class BestMatchFallbackTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())

    private fun row(id: String, height: Long = 1080, releaseType: String? = null, dynamicRange: List<String> = emptyList()) =
        SourceCandidate(id, "addon:$id", "Source $id", quality_height = height, release_type = releaseType, dynamic_range = dynamicRange, delivery = "direct")

    private class Fallback(fixture: LocalSetupFixture, val rows: List<SourceCandidate>) {
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit): SourcesOutcome {
                publish(rows)
                return SourcesOutcome(rows)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, deviceMaxHeight: Int?): RankedSources =
                RankedSources(sources, interimSourceGroups(sources))
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example.test/$id")
            }
        }
    }

    private fun fallback(rows: List<SourceCandidate>, startDeadlineMillis: Long = 60_000,
        check: suspend (ApplicationController, Fallback) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val fake = Fallback(fixture, rows)
        val controller = ApplicationController(fixture.scope, fixture.store, localFactory = { fake.device },
            bestMatchNoticeMillis = 20, startDeadlineMillis = startDeadlineMillis)
        controller.local = fake.device
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "best")))
        try {
            controller.chooseSource(movie)
            withTimeout(5000) { controller.state.first { it.playback != null } }
            check(controller, fake)
        } finally { fixture.close(controller) }
    }

    private suspend fun awaitPlayback(controller: ApplicationController, id: String) =
        withTimeout(5000) { controller.state.first { it.playingSource?.id == id && it.playback != null } }

    private suspend fun awaitOpened(fake: Fallback, id: String) =
        withTimeout(5000) { while (id !in fake.opened) delay(5) }

    @Test fun aFailedStartShowsTheNoticeThenOpensTheNextRowThatSurvivesTheHardFilters() =
        fallback(listOf(row("a"), row("cam", 2160, "cam"), row("b"))) { controller, fake ->
            assertEquals(listOf("a"), fake.opened)
            controller.observePlayback(PlayerFact.Failed)
            val notice = controller.state.value.fallbackNotice
            assertNotNull(notice)
            assertEquals("b", notice.next.id, "The camera release is never a fallback target")
            assertEquals("This source could not play. Trying the next best source…", notice.message)
            assertEquals("This source could not play. Choose another source.", controller.state.value.error,
                "Stop leaves the existing failure screen's description")
            awaitPlayback(controller, "b")
            assertNull(controller.state.value.fallbackNotice)
            assertEquals(listOf("a", "b"), fake.opened)
            assertNull(controller.state.value.error, "The next attempt clears the failure")
        }

    @Test fun stopKeepsTheFailureScreenAndNeverAdvances() =
        fallback(listOf(row("a"), row("b"))) { controller, fake ->
            controller.observePlayback(PlayerFact.Failed)
            assertNotNull(controller.state.value.fallbackNotice)
            controller.stopBestMatchFallback()
            assertNull(controller.state.value.fallbackNotice)
            assertNull(controller.state.value.bestMatch, "Stop ends the session")
            assertNotNull(controller.state.value.error)
            delay(200)
            assertEquals(listOf("a"), fake.opened)
        }

    @Test fun threeFailedStartsReturnToThePickerWithTheBannerAndTheFailedRows() =
        fallback(listOf(row("a"), row("b"), row("c"), row("d"))) { controller, fake ->
            controller.observePlayback(PlayerFact.Failed)
            awaitPlayback(controller, "b")
            controller.observePlayback(PlayerFact.Failed)
            awaitPlayback(controller, "c")
            controller.observePlayback(PlayerFact.Failed)
            withTimeout(5000) { controller.state.first { it.screen == Screen.Sources } }
            val state = controller.state.value
            assertNull(state.playback)
            assertNull(state.error)
            assertEquals("3 sources couldn’t start on this device. Choose a source.", state.sourcesBanner())
            assertEquals(listOf("a", "b", "c"), state.failedSources(), "Each failed row keeps its ranked position and label")
            assertTrue(state.bestMatch?.exhausted == true)
            assertEquals(listOf("a", "b", "c"), fake.opened, "The fourth row is never reached")
        }

    @Test fun aManualPickNeverFallsBack() =
        fallback(listOf(row("a"), row("b"))) { controller, fake ->
            controller.playSource(controller.state.value.sources.first { it.id == "b" })
            awaitPlayback(controller, "b")
            controller.observePlayback(PlayerFact.Failed)
            assertNull(controller.state.value.fallbackNotice)
            assertNotNull(controller.state.value.error)
            delay(200)
            assertEquals(listOf("a", "b"), fake.opened)
        }

    @Test fun aFailureAfterTheFirstFrameNeverSwitchesSource() =
        fallback(listOf(row("a"), row("b"))) { controller, fake ->
            controller.observePlayback(PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted))
            assertTrue(controller.state.value.bestMatch?.started == true)
            controller.observePlayback(PlayerFact.Failed)
            assertNull(controller.state.value.fallbackNotice, "A mid-playback stall keeps the existing failure behaviour")
            delay(200)
            assertEquals(listOf("a"), fake.opened)
        }

    @Test fun aDolbyVisionDecodeFailureDemotesTheOtherRiskyRows() =
        fallback(listOf(row("a", 2160, "web_dl", listOf("dv")), row("b", 2160, "web_dl", listOf("dv")), row("c"))) { controller, fake ->
            controller.observePlayback(PlayerFact.Undecodable)
            assertEquals("c", controller.state.value.fallbackNotice?.next?.id)
            awaitPlayback(controller, "c")
        }

    @Test fun noFirstFrameWithinTheStartupDeadlineFailsTheStart() =
        fallback(listOf(row("a"), row("b")), startDeadlineMillis = 120) { controller, fake ->
            withTimeout(5000) { controller.state.first { it.fallbackNotice != null } }
            assertEquals("This source did not start. Trying the next best source…", controller.state.value.fallbackNotice?.message)
            awaitPlayback(controller, "b")
            assertEquals(listOf("a", "b"), fake.opened)
        }

    @Test fun theNoAudioNoticeNamesThePreferredLanguageAndNeedsReportedTracks() {
        val tracks = org.viptv.app.player.PlaybackTracks(audio = listOf(
            org.viptv.app.player.PlaybackTrack(1, "English", true, "en"),
            org.viptv.app.player.PlaybackTrack(2, "English 5.1", false, "en-US")))
        assertEquals("This source has no French audio.", missingAudioNotice("fr", tracks))
        assertNull(missingAudioNotice("en", tracks))
        assertNull(missingAudioNotice("EN-gb", tracks))
        assertNull(missingAudioNotice(null, tracks), "Original language is unknown to the client")
        assertNull(missingAudioNotice("fr", org.viptv.app.player.PlaybackTracks()), "Tracks without languages prove nothing")
    }

    @Test fun failedRowsKeepTheirRankedPositionsAndGroups() {
        val ranked = listOf(row("a"), row("b"), row("c"))
        val groups = interimSourceGroups(ranked)
        val rows = org.viptv.app.features.sources.sourceListRows(ranked, groups, listOf("a"))
        assertEquals(listOf("a", "b", "c"), rows.map { it.source.id })
        assertEquals(listOf(true, false, false), rows.map { it.didntStart })
        assertTrue(rows.first().bestMatch, "The marker does not move off the row that failed")
    }
}
