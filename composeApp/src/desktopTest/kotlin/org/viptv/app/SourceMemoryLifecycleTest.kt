package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SourceMemoryLifecycleTest {
    private val series = Card(MediaIdentity("show", "series", emptyList()), provider_id = "addon:1", title = "Show", badges = emptyList())
    private fun episode(season: Long, number: Long) = series.copy(
        identity = MediaIdentity("show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series.identity, title = "Episode $number")
    private fun row(id: String, provider: String = "addon:$id", languages: List<String> = emptyList()) =
        SourceCandidate(id, provider, "Source $id", audio_languages = languages)

    private class Memory(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    private inner class Harness(val memory: Memory, val rowsFor: Map<String, List<SourceCandidate>>) {
        val fixture = LocalSetupFixture()
        val opened = mutableListOf<String>()
        var clock: Long = 0L
        var failSources = false

        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot =
                DetailSnapshot(series, episodes = (1L..4L).map { episode(1, it) })
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                if (failSources) throw java.io.IOException("offline")
                val candidates = rowsFor[card.identity.id].orEmpty()
                publish(SourcesOutcome(candidates))
                return SourcesOutcome(candidates)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources =
                RankedSources(sources, interimSourceGroups(sources))
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
            override suspend fun subtitles(profile: String, card: Card): SubtitleSnapshot =
                SubtitleSnapshot(Scope("", "", "", 0), emptyList(), emptyList(), complete = true)
            override suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?) = Unit
            override suspend fun profilePreferences(profile: String): PreferencesSnapshot = PreferencesSnapshot()
        }
        val controller = ApplicationController(fixture.scope, fixture.store, localFactory = { device }).also { controller ->
            controller.local = device
            controller.sourceMemory = SourceMemoryBook(memory)
            controller.capability = { DeviceCapability() }
            controller.clockMillis = { clock }
            controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "ask")))
        }
        fun close() = fixture.close(controller)
    }

    private suspend fun settle(controller: ApplicationController) = withTimeout(15_000) {
        while (true) {
            controller.sourceDetailJob?.join()
            val job = controller.job ?: return@withTimeout
            job.join()
            if (controller.job === job && controller.sourceDetailJob?.isActive != true) return@withTimeout
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(15_000) {
        while (!predicate()) delay(10)
    }

    @Test fun theRememberedLifecyclePreparesHandsOffRestartsAndRecovers() = runBlocking<Unit> {
        val memory = Memory()
        var now = 1_000_000L

        // A new title has no memory: the picker opens, and picking a source manually starts it and is remembered.
        val live = Harness(memory, rowsFor = mapOf(
            "show:1:1" to listOf(row("a", languages = listOf("en")), row("b", languages = listOf("ja"))),
            "show:1:2" to listOf(row("a2", languages = listOf("en"))),
        )).also { it.clock = now }
        try {
            val controller = live.controller
            controller.playTitle(episode(1, 1)); settle(controller)
            assertEquals(Screen.Sources, controller.state.value.screen, "A new title opens the picker, not playback")
            assertEquals(listOf("a", "b"), controller.state.value.sources.map { it.id })
            assertTrue(live.opened.isEmpty())

            controller.playSource(controller.state.value.sources.first { it.id == "a" }); settle(controller)
            live.observePresentation()
            assertEquals(RememberedSource("addon:a", listOf("en")), controller.sourceMemory.entry("default", episode(1, 2)),
                "A started manual pick is remembered for the whole series")
            assertEquals(Screen.Player, controller.state.value.screen)

            // Three minutes before the end, the next episode's source is prepared once without opening it.
            await { controller.state.value.nextEpisode?.identity?.id == "show:1:2" && !controller.state.value.nextEpisodeLoading }
            controller.observePlaybackFor(controller.state.value.playback, PlayerFact.Progress(1_250_000, 1_400_000))
            await { controller.state.value.nextEpisodePreparation?.status == NextEpisodePreparationStatus.Ready }
            assertEquals("a2", controller.state.value.nextEpisodePreparation?.source?.id)
            assertEquals(listOf("a"), live.opened, "Preparation discovers and ranks but never opens a source")

            // Next hands the prepared source off without the picker or a duplicate open.
            controller.playEpisode(episode(1, 2)); settle(controller)
            assertEquals(Screen.Player, controller.state.value.screen, "Next hands off without the picker")
            assertEquals("a2", controller.state.value.playingSource?.id)
            assertEquals(listOf("a", "a2"), live.opened)
            assertNull(controller.state.value.nextEpisodePreparation, "The handoff consumes the preparation")

            // A start that then fails never claims it is ready: it returns to the picker with nothing playing.
            val handed = controller.state.value.playback
            controller.observePlaybackFor(handed, PlayerFact.Failed); settle(controller)
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertNull(controller.state.value.playback, "A failed start leaves no playback")
            assertNull(controller.state.value.playingSource)
        } finally { live.close() }

        // Five days later a fresh controller reads the same persisted store and re-resolves a fresh URL, no picker.
        now += 5 * 24 * 60 * 60 * 1000L
        val restarted = Harness(memory, rowsFor = mapOf("show:1:2" to listOf(row("a2-restart", languages = listOf("en"))))).also { it.clock = now }
        try {
            val controller = restarted.controller
            controller.playTitle(episode(1, 2)); settle(controller)
            assertEquals(Screen.Player, controller.state.value.screen, "A remembered choice survives five days and starts without the picker")
            assertEquals("a2-restart", controller.state.value.playingSource?.id)
            assertEquals("https://media.example/a2-restart", controller.state.value.playback?.uri, "The expired URL is re-resolved, never reused")
            assertEquals(listOf("a2-restart"), restarted.opened)
        } finally { restarted.close() }

        // No row in the remembered audio opens the picker instead of silently switching language.
        val fallback = Harness(memory, rowsFor = mapOf("show:1:3" to listOf(row("ja-only", languages = listOf("ja"))))).also { it.clock = now }
        try {
            val controller = fallback.controller
            controller.playTitle(episode(1, 3)); settle(controller)
            assertTrue(fallback.opened.isEmpty(), "Nothing plays outside the remembered audio")
            assertEquals(Screen.Sources, controller.state.value.screen, "No same-language match opens the picker")
            assertNull(controller.state.value.playback)
            assertEquals(listOf("ja-only"), controller.state.value.sources.map { it.id })
        } finally { fallback.close() }

        // Network loss while re-resolving the remembered choice surfaces an error, never a false readiness claim.
        val offline = Harness(memory, rowsFor = mapOf("show:1:3" to listOf(row("a3", languages = listOf("en"))))).also {
            it.clock = now; it.failSources = true
        }
        try {
            val controller = offline.controller
            controller.playTitle(episode(1, 3)); settle(controller)
            assertNull(controller.state.value.playback, "An unreachable provider never starts playback")
            assertNull(controller.state.value.nextEpisodePreparation)
            assertNotNull(controller.state.value.error, "The failure surfaces as an error")
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertTrue(offline.opened.isEmpty())
        } finally { offline.close() }
    }

    private fun Harness.observePresentation() {
        val input = controller.state.value.playback ?: fail("nothing is playing")
        controller.observePlaybackFor(input, PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
    }
}