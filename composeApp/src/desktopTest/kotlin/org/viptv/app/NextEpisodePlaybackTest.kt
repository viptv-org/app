package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class NextEpisodePlaybackTest {
    private val parent = Card(MediaIdentity("parent", "series", emptyList()), provider_id = "addon:7", title = "Series", badges = emptyList())
    private fun episode(number: Long) = parent.copy(identity = MediaIdentity("episode-$number", "series", emptyList(),
        EpisodeCoordinates("aired", 1, number)), parent_identity = parent.identity)
    private fun playing() = PlaybackInput("current", "https://media.example/fixture.mp4", profileId = "owner")

    private class Device(fixture: LocalSetupFixture, private val rows: List<SourceCandidate>) : LocalConnection by fixture.local {
        val opened = mutableListOf<String>()
        val recorded = mutableListOf<Pair<Long, String>>()
        val releasing = CompletableDeferred<Unit>()
        val listening = CompletableDeferred<Unit>()
        var holdOpen = false
        var holdSources = false

        override suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit): SourcesOutcome {
            listening.complete(Unit)
            if (holdSources) releasing.await()
            publish(rows)
            return SourcesOutcome(rows)
        }

        override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, deviceMaxHeight: Int?): RankedSources {
            val ranked = sources.sortedByDescending { it.quality_height }
            return RankedSources(ranked, interimSourceGroups(ranked), bestMatchFullyMatches = true)
        }

        override suspend fun openSource(id: String): PlaybackInput {
            opened += id
            if (holdOpen) releasing.await()
            return PlaybackInput("next-$id", "https://media.example/next.mp4", profileId = "owner")
        }

        override suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?) {
            recorded += positionMillis to card.identity.id
        }
    }

    private val rows = listOf(SourceCandidate("plain", "addon:plain", "Plain", quality_height = 720),
        SourceCandidate("best", "addon:best", "Best", quality_height = 2160))

    private fun transition(selection: String = "ask", rows: List<SourceCandidate> = this.rows,
        holdOpen: Boolean = false, holdSources: Boolean = false, block: suspend (ApplicationController, Device) -> Unit) = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val device = Device(fixture, rows).also { it.holdOpen = holdOpen; it.holdSources = holdSources }
        val controller = fixture.controller { device }
        controller.local = device
        controller.mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = "owner", localMode = true,
            playback = playing(), sourceTitle = episode(24), detail = DetailSnapshot(parent, episodes = (1L..25L).map(::episode)),
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = selection)))
        try { block(controller, device) }
        finally { device.releasing.complete(Unit); fixture.close(controller) }
    }

    private suspend fun settle(controller: ApplicationController) {
        while (true) { val job = controller.job ?: return; job.join(); if (controller.job === job) return }
    }

    @Test fun theNextEpisodePlaysWhateverTheDefaultSourceSays() = transition("ask") { controller, device ->
        controller.observePlaybackFor(requireNotNull(controller.state.value.playback), PlayerFact.Progress(600_000, 1_800_000))
        controller.playEpisode(episode(25))
        settle(controller)
        assertEquals(listOf("best"), device.opened, "The next episode starts on its own, without the picker")
        assertEquals(Screen.Player, controller.state.value.screen)
        assertEquals("next-best", controller.state.value.playback?.id)
        assertEquals(episode(25), controller.state.value.sourceTitle)
        assertEquals(listOf(600_000L to "episode-24"), device.recorded, "The outgoing episode keeps its position")
    }

    @Test fun bestMatchProfilesStartTheNextEpisodeTheSameWay() = transition("best") { controller, device ->
        controller.playEpisode(episode(25))
        settle(controller)
        assertEquals(listOf("best"), device.opened)
        assertEquals(Screen.Player, controller.state.value.screen)
    }

    @Test fun anotherEpisodeFromThePlayerKeepsAskingForASource() = transition("ask") { controller, device ->
        controller.playEpisode(episode(3))
        settle(controller)
        assertTrue(device.opened.isEmpty(), "Only a successor starts on its own")
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertNull(controller.state.value.playback)
        assertEquals(episode(3), controller.state.value.sourceTitle)
    }

    @Test fun anEpisodeThatIsNotTheSuccessorNeverStartsOnItsOwn() = transition("ask") { controller, device ->
        listOf(episode(24), episode(26), episode(25).copy(provider_id = "addon:8")).forEach { card ->
            controller.playEpisode(card)
            settle(controller)
            assertTrue(device.opened.isEmpty(), "${card.identity.id} is not a successor of the episode playing")
            assertEquals(Screen.Sources, controller.state.value.screen)
            assertNull(controller.state.value.playback)
            assertEquals(card, controller.state.value.sourceTitle)
        }
    }

    @Test fun withoutASourceThePickerStaysUsableAndPlaysNothing() = transition("ask", rows = emptyList()) { controller, device ->
        controller.playEpisode(episode(25))
        settle(controller)
        assertTrue(device.opened.isEmpty(), "Nothing plays without a source")
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertNull(controller.state.value.playback)
        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.findingSources)
        assertNull(controller.state.value.error)
    }

    @Test fun backingOutOfTheTransitionPlaysNothing() = transition("ask", holdSources = true) { controller, device ->
        controller.playEpisode(episode(25))
        withTimeout(5_000) { device.listening.await() }
        controller.backSources()
        device.releasing.complete(Unit)
        settle(controller)
        assertTrue(device.opened.isEmpty(), "The viewer left the picker before a source opened")
        assertNull(controller.state.value.playback)
    }

    @Test fun anOpenThatOutlivesItsProfileNeverStartsThatEpisode() = transition("ask", holdOpen = true) { controller, device ->
        controller.playEpisode(episode(25))
        withTimeout(5_000) { while (device.opened.isEmpty()) delay(5) }
        controller.mutable.value = controller.mutable.value.copy(selectedProfile = "other")
        device.releasing.complete(Unit)
        settle(controller)
        assertNull(controller.state.value.playback, "A profile switch cancels the episode it was preparing")
        assertNotEquals(Screen.Player, controller.state.value.screen)
    }

    @Test fun anOpenForATitleThatChangedMeanwhileNeverStartsPlayback() = transition("ask", holdOpen = true) { controller, device ->
        controller.playEpisode(episode(25))
        withTimeout(5_000) { while (device.opened.isEmpty()) delay(5) }
        controller.mutable.value = controller.mutable.value.copy(sourceTitle = episode(9))
        device.releasing.complete(Unit)
        settle(controller)
        assertNull(controller.state.value.playback)
        assertNotEquals(Screen.Player, controller.state.value.screen)
    }
}