package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.TimingOffsets
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ReleaseOffsetsMemoryTest {
    private val series = Card(MediaIdentity("show", "series", emptyList()), provider_id = "addon:1", title = "Show", badges = emptyList())
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series.identity, title = "Episode $number")
    private fun row(id: String, provider: String = "addon:a", binge: String? = null, fingerprint: String? = null) =
        SourceCandidate(id, provider, "Source $id", binge_group = binge, source_fingerprint = fingerprint)

    private class Memory(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    @Test fun theBookKeysByProviderAndBingeGroupElseFingerprint() {
        val memory = Memory()
        val book = SourceMemoryBook(memory)
        book.rememberOffsets("default", row("e1", binge = "subsplease-1080"), TimingOffsets(300_000, -1_200_000))
        book.rememberOffsets("default", row("f1", provider = "addon:b", fingerprint = "abc"), TimingOffsets(audioUs = -400_000))
        val restarted = SourceMemoryBook(memory)
        assertEquals(TimingOffsets(300_000, -1_200_000), restarted.offsets("default", row("e2", binge = "subsplease-1080")))
        assertEquals(TimingOffsets(), restarted.offsets("default", row("e2", provider = "addon:c", binge = "subsplease-1080")))
        assertEquals(TimingOffsets(), restarted.offsets("default", row("e2", binge = "other-group")))
        assertEquals(TimingOffsets(), restarted.offsets("guest", row("e2", binge = "subsplease-1080")))
        assertEquals(TimingOffsets(audioUs = -400_000), restarted.offsets("default", row("f2", provider = "addon:x", fingerprint = "abc")))
        assertEquals(TimingOffsets(), restarted.offsets("default", row("plain")))
        restarted.rememberOffsets("default", row("e3", binge = "subsplease-1080"), TimingOffsets())
        assertEquals(TimingOffsets(), SourceMemoryBook(memory).offsets("default", row("e4", binge = "subsplease-1080")))
    }

    @Test fun theNextEpisodeOfTheSameReleaseStartsWithTheCorrectionAndAnotherSourceStartsAtZero() = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val device = object : LocalConnection by fixture.local {
            override suspend fun openSource(id: String): PlaybackInput = PlaybackInput(id, "https://media.example/$id", profileId = "default")
            override suspend fun profilePreferences(profile: String): PreferencesSnapshot = PreferencesSnapshot()
        }
        val controller = ApplicationController(fixture.scope, fixture.store, localFactory = { device }).also {
            it.local = device
            it.sourceMemory = SourceMemoryBook(Memory())
            it.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
                preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "ask")))
        }
        suspend fun play(card: Card, source: SourceCandidate): PlaybackInput {
            controller.updateState { it.copy(sourceTitle = card, sources = listOf(source)) }
            controller.playSource(source)
            return withTimeout(5_000) {
                while (controller.state.value.playback?.id != source.id) delay(10)
                requireNotNull(controller.state.value.playback)
            }
        }
        suspend fun until(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }
        try {
            val first = play(episode(1), row("e1", binge = "release"))
            assertEquals(TimingOffsets(), first.offsets.state.value)
            repeat(3) { first.offsets.shiftAudio(100_000) }
            first.offsets.shiftSubtitle(-500_000)
            until { controller.sourceMemory.offsets("default", row("any", binge = "release")) == TimingOffsets(300_000, -500_000) }
            assertEquals("e1", controller.state.value.playback?.id, "Changing the offsets never reopens the stream")
            controller.closePlayer()

            val next = play(episode(2), row("e2", binge = "release"))
            assertEquals(TimingOffsets(300_000, -500_000), next.offsets.state.value)
            assertEquals(next.offsets.state.value, next.offsets.remembered)
            controller.closePlayer()

            val other = play(episode(2), row("o2", provider = "addon:b", binge = "release"))
            assertEquals(TimingOffsets(), other.offsets.state.value)
        } finally { fixture.close(controller) }
    }
}
