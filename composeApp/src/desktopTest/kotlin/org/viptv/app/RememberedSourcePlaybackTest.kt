package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class RememberedSourcePlaybackTest {
    private val series = Card(MediaIdentity("show", "series", emptyList()), provider_id = "addon:1", title = "Show", badges = emptyList())
    private val otherSeries = Card(MediaIdentity("other", "series", emptyList()), provider_id = "addon:1", title = "Other", badges = emptyList())
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number")
    private fun otherEpisode(season: Long, number: Long) = otherSeries.copy(
        identity = MediaIdentity("other:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = otherSeries.identity, title = "Other $number")
    private fun row(id: String, provider: String = "addon:$id", languages: List<String> = emptyList()) =
        SourceCandidate(id, provider, "Source $id", audio_languages = languages)

    private class Memory(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    private inner class Harness(var rows: (Card) -> List<SourceCandidate> = { emptyList() }) {
        val fixture = LocalSetupFixture()
        val memory = Memory()
        val discovered = mutableListOf<Card>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot =
                DetailSnapshot(series, episodes = listOf(episode(1, 1), episode(1, 2), otherEpisode(1, 1), otherEpisode(1, 2)))
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                discovered += card
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
            override suspend fun profilePreferences(profile: String): PreferencesSnapshot = PreferencesSnapshot()
        }
        val controller = ApplicationController(fixture.scope, fixture.store, localFactory = { device }).also { controller ->
            controller.local = device
            controller.sourceMemory = SourceMemoryBook(memory)
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "ask")))
        }
        fun close() = fixture.close(controller)
    }

    private suspend fun settle(controller: ApplicationController) = withTimeout(5_000) {
        while (true) {
            controller.sourceDetailJob?.join()
            val job = controller.job ?: return@withTimeout
            job.join()
            if (controller.job === job && controller.sourceDetailJob?.isActive != true) return@withTimeout
        }
    }

    private fun remember(harness: Harness, card: Card = episode(1, 1), source: SourceCandidate = row("a", languages = listOf("en"))) =
        harness.controller.sourceMemory.remember("default", card, source)

    private fun Harness.play(card: Card, source: SourceCandidate) {
        controller.updateState { it.copy(sourceTitle = card, sources = listOf(source)) }
        controller.playSource(source)
    }

    private fun Harness.firstFrame() {
        val input = controller.state.value.playback ?: fail("nothing is playing")
        controller.observePlaybackFor(input, PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
    }

    @Test fun aManualPickThatStartsIsRememberedAndTheNextEpisodeStartsOnAFreshMatchingSource() = runBlocking<Unit> {
        val harness = Harness { listOf(row("a", languages = listOf("en")), row("b", languages = listOf("ja"))) }
        val controller = harness.controller
        try {
            controller.playTitle(episode(1, 1))
            settle(controller)
            assertEquals(Screen.Sources, controller.state.value.screen, "The first play of a title has no memory and shows the picker")
            controller.playSource(controller.state.value.sources.first { it.id == "a" })
            settle(controller)
            harness.firstFrame()
            assertEquals(RememberedSource("addon:a", listOf("en")), harness.controller.sourceMemory.entry("default", episode(1, 2)),
                "A started manual pick is remembered for the series")

            harness.rows = { listOf(row("a-next", languages = listOf("en"))) }
            controller.closePlayer()
            controller.playTitle(episode(1, 2))
            settle(controller)
            assertEquals(listOf("a", "a-next"), harness.opened, "The next episode re-resolves a fresh source instead of reusing the old one")
            assertEquals("a-next", controller.state.value.playingSource?.id)
            assertEquals(Screen.Player, controller.state.value.screen, "Playback opens without the viewer picking a source")
            assertEquals("https://media.example/a-next", controller.state.value.playback?.uri)
            assertEquals(episode(1, 2), harness.discovered.last(), "The remembered source is matched against a fresh discovery for this episode")
        } finally { harness.close() }
    }

    @Test fun noMatchingLanguageOpensThePickerInsteadOfSwitchingAudio() = runBlocking<Unit> {
        val harness = Harness { listOf(row("a", provider = "addon:a", languages = listOf("ja")), row("b", provider = "addon:b", languages = listOf("ja"))) }
        val controller = harness.controller
        try {
            remember(harness, source = row("a", provider = "addon:a", languages = listOf("en")))
            controller.playTitle(episode(1, 2))
            settle(controller)
            assertTrue(harness.opened.isEmpty(), "Nothing starts on a source without the remembered audio")
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertEquals(listOf("a", "b"), controller.state.value.sources.map { it.id })
            assertFalse(controller.state.value.findingSources)
        } finally { harness.close() }
    }

    @Test fun aFailedStartDoesNotReplaceTheLastSuccessfulChoice() = runBlocking<Unit> {
        val harness = Harness()
        val controller = harness.controller
        try {
            remember(harness)
            harness.play(episode(1, 1), row("b", languages = listOf("ja")))
            settle(controller)
            controller.observePlaybackFor(controller.state.value.playback, PlayerFact.Failed)
            assertEquals(RememberedSource("addon:a", listOf("en")), controller.sourceMemory.entry("default", episode(1, 1)))
        } finally { harness.close() }
    }

    @Test fun anotherProfileOrUnrelatedTitleNeverReuses() = runBlocking<Unit> {
        val harness = Harness { listOf(row("a", languages = listOf("en"))) }
        val controller = harness.controller
        try {
            remember(harness)
            controller.updateState { it.copy(selectedProfile = "guest") }
            controller.playTitle(episode(1, 2))
            settle(controller)
            assertTrue(harness.opened.isEmpty(), "Another profile never inherits the choice")
            assertEquals(Screen.Sources, controller.state.value.screen)

            controller.updateState { it.copy(selectedProfile = "default", screen = Screen.Details) }
            controller.playTitle(otherEpisode(1, 1))
            settle(controller)
            assertTrue(harness.opened.isEmpty(), "An unrelated title never inherits the choice")
            assertEquals(Screen.Sources, controller.state.value.screen)
        } finally { harness.close() }
    }

    @Test fun aRestartKeepsTheChoiceAndObtainsAFreshSource() = runBlocking<Unit> {
        val first = Harness { listOf(row("a", languages = listOf("en"))) }
        remember(first)
        first.close()
        val restarted = Harness { listOf(row("a-new", languages = listOf("en"))) }
        restarted.memory.raw = first.memory.raw
        restarted.controller.sourceMemory = SourceMemoryBook(restarted.memory)
        val controller = restarted.controller
        try {
            controller.playTitle(episode(1, 2))
            settle(controller)
            assertEquals(listOf("a-new"), restarted.opened)
            assertEquals("https://media.example/a-new", controller.state.value.playback?.uri)
            assertEquals(Screen.Player, controller.state.value.screen)
        } finally { restarted.close() }
    }

    @Test fun anExplicitChooseSourceStillAsks() = runBlocking<Unit> {
        val harness = Harness { listOf(row("a", languages = listOf("en"))) }
        val controller = harness.controller
        try {
            remember(harness)
            controller.chooseSource(episode(1, 2))
            settle(controller)
            assertTrue(harness.opened.isEmpty(), "Choose source is an explicit request for the picker")
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertEquals(listOf("a"), controller.state.value.sources.map { it.id })
        } finally { harness.close() }
    }

    @Test fun aRememberedStartThatFailsFallsBackOverTheFreshRows() = runBlocking<Unit> {
        val harness = Harness { listOf(row("remembered", provider = "addon:a", languages = listOf("en")), row("backup", provider = "addon:b", languages = listOf("en"))) }
        val controller = harness.controller
        try {
            remember(harness, source = row("remembered", provider = "addon:a", languages = listOf("en")))
            controller.playTitle(episode(1, 2))
            settle(controller)
            assertEquals("remembered", controller.state.value.playingSource?.id)
            controller.observePlaybackFor(controller.state.value.playback, PlayerFact.Failed)
            delay(2_100)
            settle(controller)
            assertEquals("backup", controller.state.value.playingSource?.id)
            assertEquals(listOf("remembered", "backup"), harness.opened)
        } finally { harness.close() }
    }
}
