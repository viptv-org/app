package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderAffinityTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "fixture", title = "Movie", badges = emptyList())

    private class Memory(var raw: String? = null) : SourceMemoryStore {
        override fun read(): String? = raw
        override fun write(value: String) { raw = value }
    }

    private class Device(fixture: LocalSetupFixture, private val rows: List<SourceCandidate>, private val failOpen: Boolean = false) {
        val opened = mutableListOf<String>()
        val contexts = mutableListOf<RankingContext>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun sources(profile: String, card: Card, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                publish(SourcesOutcome(rows))
                return SourcesOutcome(rows)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources {
                contexts += context
                return RankedSources(sources, interimSourceGroups(sources), bestMatchFullyMatches = true)
            }
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                if (failOpen) throw LocalFailure("This source could not play.")
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
    }

    private fun runBest(failOpen: Boolean = false, affinity: String? = null,
        check: suspend TestScope.(ApplicationController, Device, Memory) -> Unit) = runTest {
        val fixture = LocalSetupFixture()
        val memory = Memory()
        affinity?.let { SourceMemoryBook(memory).rememberAffinity("default", it) }
        val device = Device(fixture, listOf(SourceCandidate("a", "addon:a", "Source a", quality_height = 1080)), failOpen)
        val controller = ApplicationController(backgroundScope, fixture.store, localFactory = { device.device })
        controller.local = device.device
        controller.capability = { DeviceCapability() }
        controller.sourceMemory = SourceMemoryBook(memory)
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            preferences = PreferencesSnapshot(preferences = ProfilePreferences(source_selection = "best")))
        try {
            controller.chooseSource(movie)
            runCurrent()
            advanceTimeBy(1_500); runCurrent()
            check(controller, device, memory)
        } finally { fixture.close(controller) }
    }

    private fun ApplicationController.presentFrame() {
        val input = requireNotNull(state.value.playback)
        observePlaybackFor(input, PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
    }

    @Test fun anAutomaticStartWritesTheProviderAffinityOnItsFirstFrame() = runBest { controller, device, memory ->
        assertEquals(listOf("a"), device.opened)
        assertNull(controller.sourceMemory.affinity("default"), "A start that has not presented a frame writes nothing")

        controller.presentFrame()
        assertEquals("addon:a", controller.sourceMemory.affinity("default"), "An automatic start that presents a frame writes it too")
        assertEquals("addon:a", SourceMemoryBook(memory).affinity("default"), "The write lands in the device store")
    }

    @Test fun aStartThatNeverPresentsAFrameLeavesTheAffinityAlone() = runBest(failOpen = true, affinity = "addon:previous") { controller, device, _ ->
        assertEquals(listOf("a"), device.opened)
        assertNull(controller.state.value.playback)
        assertEquals("addon:previous", controller.sourceMemory.affinity("default"), "A failed start never replaces the stored provider")
    }

    @Test fun discoverySendsTheStoredAffinityWithTheRanking() = runBest(affinity = "addon:previous") { controller, device, _ ->
        assertEquals("addon:previous", device.contexts.single().providerAffinity)
    }

    @Test fun discoverySendsNoAffinityForAProfileWithoutOne() = runBest { _, device, _ ->
        assertNull(device.contexts.single().providerAffinity)
    }

    @Test fun theRankingRequestsCarryTheAffinityOnlyWhenValid() {
        val row = SourceCandidate("a", "addon:a", "Source a", quality_height = 1080)
        val withAffinity = RankingContext(providerAffinity = "debrid:tb")
        assertEquals("debrid:tb", rankingContextJson(withAffinity).getValue("provider_affinity").jsonPrimitive.content)
        assertEquals("debrid:tb", sourceRankingRequest(listOf(row), withAffinity).getValue("provider_affinity").jsonPrimitive.content)
        assertNull(rankingContextJson(RankingContext()).get("provider_affinity"))
        assertNull(sourceRankingRequest(listOf(row), RankingContext()).get("provider_affinity"))
        for (invalid in listOf("", "  ", "addon\n7", "a".repeat(129))) {
            val context = RankingContext(providerAffinity = invalid)
            assertNull(rankingContextJson(context).get("provider_affinity"), invalid)
            assertNull(sourceRankingRequest(listOf(row), context).get("provider_affinity"), invalid)
        }
    }
}