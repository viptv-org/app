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

class PreferredSourceRecoveryTest {
    private val movie = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "catalog", title = "Movie", badges = emptyList())

    @Test fun failedPreferredPlaybackExpandsOnceAndUsesOnlyCoreReuseIdsWithoutReplacingTheManualChoice() = runBlocking {
        val fixture = LocalSetupFixture()
        val preferred = SourceCandidate("preferred", "supplier-a", "Preferred", audio_languages = listOf("en"))
        val alternate = preferred.copy(id = "preferred-alt", label = "Alternative")
        val wrong = SourceCandidate("ineligible", "supplier-b", "Rejected by Core", audio_languages = listOf("en"))
        val backup = SourceCandidate("backup", "supplier-c", "Backup", audio_languages = listOf("en"))
        val exclusions = mutableListOf<Boolean>()
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local, PreferredSourceDiscovery {
            override suspend fun discoverSources(profile: String, card: Card, context: RankingContext, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                exclusions += context.excludePreferred
                return SourcesOutcome(if (context.excludePreferred) listOf(wrong, backup) else listOf(preferred, alternate),
                    discoveryPhase = if (context.excludePreferred) "fallback" else "preferred").also(publish)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources =
                RankedSources(sources, interimSourceGroups(sources), reuseSupported = true,
                    reuseIds = if (context.excludePreferred) listOf(backup.id) else listOf(preferred.id, alternate.id),
                    reuseReady = true, automaticIds = sources.map { it.id }, reusePreferred = !context.excludePreferred)
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
        val controller = ApplicationController(fixture.scope, fixture.store, bestMatchNoticeMillis = 1)
        controller.local = device
        controller.capability = { DeviceCapability() }
        controller.sourceMemory.setAcross("default", true)
        controller.sourceMemory.remember("default", movie, preferred)
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            detail = DetailSnapshot(movie, episodes = emptyList()), preferences = PreferencesSnapshot())
        try {
            controller.playTitle(movie)
            val first = withTimeout(5_000) { controller.state.first { it.playback?.id == preferred.id } }
            controller.observePlaybackFor(first.playback, PlayerFact.Failed)
            val second = withTimeout(5_000) { controller.state.first { it.playback?.id == alternate.id } }
            assertEquals(listOf(false), exclusions)
            controller.observePlaybackFor(second.playback, PlayerFact.Failed)
            val recovered = withTimeout(5_000) { controller.state.first { it.playback?.id == backup.id } }
            controller.observePlaybackFor(recovered.playback, PlayerFact.Presentation(PresentationProof.NativeRendererPresented))
            assertEquals(listOf(false, true), exclusions)
            assertEquals(listOf(preferred.id, alternate.id, backup.id), opened)
            assertEquals(3, controller.state.value.bestMatch?.attempts)
            assertEquals(preferred.provider_id, controller.sourceMemory.entry("default", movie)?.provider_id)
            assertEquals(preferred.provider_id, controller.sourceMemory.choice("default", movie.copy(identity = movie.identity.copy(id = "other")))?.provider_id)
        } finally { fixture.close(controller) }
    }
    @Test fun anAlreadyExpandedFallbackWalksCoreIdsWithoutAnotherDiscoveryAndStopsAfterThreeAttempts() = runBlocking {
        val fixture = LocalSetupFixture()
        val rows = listOf("b", "c", "d", "e").map { SourceCandidate(it, "supplier-a", it, audio_languages = listOf("en")) }
        var discoveries = 0
        val opened = mutableListOf<String>()
        val device = object : LocalConnection by fixture.local, PreferredSourceDiscovery {
            override suspend fun discoverSources(profile: String, card: Card, context: RankingContext, publish: (SourcesOutcome) -> Unit): SourcesOutcome {
                discoveries++
                return SourcesOutcome(rows, discoveryPhase = "fallback").also(publish)
            }
            override suspend fun rankSources(profile: String, sources: List<SourceCandidate>, context: RankingContext): RankedSources =
                RankedSources(sources, interimSourceGroups(sources), reuseSupported = true, reuseIds = sources.map { it.id },
                    reuseReady = true, automaticIds = sources.map { it.id }, reusePreferred = true)
            override suspend fun openSource(id: String): PlaybackInput {
                opened += id
                return PlaybackInput(id, "https://media.example/$id", profileId = "default")
            }
        }
        val controller = ApplicationController(fixture.scope, fixture.store, bestMatchNoticeMillis = 1)
        controller.local = device
        controller.capability = { DeviceCapability() }
        controller.sourceMemory.remember("default", movie, rows.first().copy(id = "old-manual"))
        controller.mutable.value = ApplicationState(screen = Screen.Details, selectedProfile = "default", localMode = true,
            detail = DetailSnapshot(movie, episodes = emptyList()), preferences = PreferencesSnapshot())
        try {
            controller.playTitle(movie)
            for (id in listOf("b", "c", "d")) {
                val state = withTimeout(5_000) { controller.state.first { it.playback?.id == id } }
                controller.observePlaybackFor(state.playback, PlayerFact.Failed)
            }
            withTimeout(5_000) { controller.state.first { it.screen == Screen.Sources && it.playback == null } }
            assertEquals(listOf("b", "c", "d"), opened)
            assertEquals(1, discoveries)
            assertEquals(3, controller.state.value.bestMatch?.attempts)
        } finally { fixture.close(controller) }
    }

}
