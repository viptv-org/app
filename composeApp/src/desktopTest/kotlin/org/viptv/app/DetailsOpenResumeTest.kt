package org.viptv.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.viptv.app.connection.LocalConnection
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.features.details.detailResume
import org.viptv.app.features.details.heroEpisode
import org.viptv.app.features.details.heroPlayLabel
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.open
import org.viptv.engine.wire.*
import kotlin.test.*

class DetailsOpenResumeTest {
    @get:Rule val uiThread = UiThreadRule()

    private val show = "tt0903747"

    private fun series() = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Breaking Bad", badges = emptyList())
    private fun episode(season: Long, number: Long, progress: Double? = null) = series().copy(
        identity = MediaIdentity("$show:$season:$number", "series", emptyList(), EpisodeCoordinates("aired", season, number)),
        parent_identity = series().identity, title = "Episode $season-$number", progress = progress)
    private val resumeCard = Card(MediaIdentity("$show:3:5", "series", emptyList(), EpisodeCoordinates("aired", 3, 5)),
        provider_id = "library", title = "Breaking Bad", subtitle = "S3 E5 · 9m left", badges = emptyList(), progress = 0.4,
        parent_identity = MediaIdentity(show, "series", emptyList()))
    private fun movie(progress: Double? = 0.4) = Card(MediaIdentity("tt0133093", "movie", listOf(ExternalId("imdb", "tt0133093"))),
        provider_id = "addon:21", title = "The Matrix", badges = emptyList(), progress = progress, metadata = CardMetadata(runtime_minutes = 136))

    private fun runIdle() = ApplicationState(screen = Screen.Home, selectedProfile = "default", localMode = true, preferences = PreferencesSnapshot())

    @Test fun aSparseMovieDetailKeepsTheTappedCardsResume() = runBlocking {
        val fixture = LocalSetupFixture()
        val gate = CompletableDeferred<Unit>()
        val sparse = DetailSnapshot(item = movie(null), episodes = emptyList())
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot { gate.await(); return sparse }
        }
        val controller = fixture.controller { device }
        controller.local = device
        controller.mutable.value = runIdle()
        try {
            controller.open(movie())
            assertEquals(0.4, controller.state.value.detail?.item?.progress, "the shell keeps the tapped card's progress")
            assertTrue(controller.state.value.detailShell)
            gate.complete(Unit)
            withTimeout(5_000) { controller.state.first { !it.busy } }
            val resolved = controller.state.value
            assertEquals(0.4, resolved.detail?.item?.progress, "the sparse movie detail keeps Resume")
            assertEquals("Resume", heroPlayLabel(resolved.detail?.item))
        } finally { gate.complete(Unit); fixture.close(controller) }
    }

    @Test fun aSparseSeriesDetailKeepsTheTappedEpisodesResume() = runBlocking {
        val fixture = LocalSetupFixture()
        val gate = CompletableDeferred<Unit>()
        val sparse = DetailSnapshot(item = series(), episodes = listOf(episode(3, 5)))
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot { gate.await(); return sparse }
        }
        val controller = fixture.controller { device }
        controller.local = device
        controller.mutable.value = runIdle()
        try {
            controller.open(resumeCard)
            gate.complete(Unit)
            withTimeout(5_000) { controller.state.first { !it.busy } }
            val loaded = controller.state.value.detail ?: fail("a detail loaded")
            val hero = detailResume(loaded, continuing = emptyList()).heroEpisode(loaded)
            assertNotNull(hero, "the sparse on-page detail still resolves the tapped episode")
            assertEquals(resumeCard.identity, hero!!.identity, "the hero plays S3 E5")
            assertEquals(series().provider_id, hero.provider_id, "in the detail's provider scope")
            assertEquals(0.4, hero.progress, "the tapped card's progress survives the sparse read")
            assertEquals("Resume S3 E5", heroPlayLabel(hero))
        } finally { gate.complete(Unit); fixture.close(controller) }
    }
}