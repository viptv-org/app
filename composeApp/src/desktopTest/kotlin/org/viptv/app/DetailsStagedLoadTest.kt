package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.components.heroMetadataParts
import org.viptv.app.components.presentationBadges
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * One staged-load harness: open a title from its card, let the full read fill in, page in the
 * remaining episodes. Every presentation slot is recorded at each stage and must only move to a
 * strictly better value — never a blank after a fill, never a shorter text and never an image
 * swapped for another of the same slot, on the data both phone and TV draw from.
 */
class DetailsStagedLoadTest {
    private val identity = MediaIdentity("tt1856010", "series", listOf(ExternalId("imdb", "tt1856010")))
    private val series = Card(identity, provider_id = "addon:21", title = "Gabby & Friends", badges = emptyList())

    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("tt1856010:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = identity, title = "Episode $number")

    private val tapped = series.copy(
        description = "A short card description.",
        poster = "https://card/poster.jpg",
        logo = null,
        metadata = CardMetadata(year = 2025, runtime_minutes = 30, genres = listOf("Comedy", "Drama"),
            rating = 8.1, rating_votes = 1200, certification = "TV-Y7", cast = emptyList()),
        badges = listOf("4K"))

    private val detailItem = series.copy(
        description = "A much longer description that arrives with the full read and must win.",
        poster = "https://detail/poster-full.jpg",
        landscape = "https://detail/backdrop.jpg",
        logo = "https://detail/logo.png",
        metadata = CardMetadata(year = null, runtime_minutes = null, genres = emptyList(), rating = null,
            rating_votes = null, certification = null, cast = listOf("Lena", "Marcus", "Piper")),
        badges = listOf("4K", "HDR", "CC"))

    private val fullDetail = DetailSnapshot(detailItem, episodes = (1L..2L).map { episode(it) },
        next_cursor = "2", seasons = listOf(EpisodeSeason(1, 4, 0)))
    private val secondPage = DetailSnapshot(detailItem, episodes = (3L..4L).map { episode(it) })

    private data class Slots(
        val title: String?, val logo: String?, val heroArt: String?, val metadataLine: String?,
        val badgeLine: List<String>, val description: String?, val cast: String?, val episodeCount: Int,
    )

    private fun slots(detail: DetailSnapshot?): Slots? {
        detail ?: return null
        val item = detail.item
        return Slots(
            title = item.title,
            logo = item.logo,
            heroArt = item.landscape ?: item.poster,
            metadataLine = item.heroMetadataParts().takeIf { it.isNotEmpty() }?.joinToString(" · "),
            badgeLine = item.presentationBadges(),
            description = item.description,
            cast = item.metadata?.cast?.takeIf { it.isNotEmpty() }?.joinToString(", "),
            episodeCount = detail.episodes.size,
        )
    }

    private fun assertMonotonic(name: String, values: List<String?>) {
        var filled = false
        values.forEach { value ->
            if (filled) assertFalse(value.isNullOrBlank(), "$name went blank after being filled: $values")
            if (!value.isNullOrBlank()) filled = true
        }
        assertTrue(values.filterNotNull().distinct().size <= 2,
            "$name changed more than once: ${values.filterNotNull().distinct()}")
    }

    @Test fun everySlotOnlyImprovesAcrossTheStagedLoad() = runBlocking {
        val fixture = LocalSetupFixture()
        val detailGate = CompletableDeferred<Unit>()
        val pageGate = CompletableDeferred<Unit>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot {
                detailGate.await()
                return fullDetail
            }
            override suspend fun detailPage(profile: String, card: Card, offset: Int, limit: Int): DetailSnapshot {
                pageGate.await()
                return secondPage
            }
        }
        val controller = fixture.controller { device }
        controller.local = device
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default",
            localMode = true, preferences = PreferencesSnapshot())
        try {
            controller.open(tapped)
            val stages = mutableListOf<Slots>()

            stages += requireNotNull(slots(controller.state.value.detail))

            detailGate.complete(Unit)
            withTimeout(5000) { controller.state.first { !it.busy } }
            assertFalse(controller.state.value.detailShell, "the full read replaces the shell")
            stages += requireNotNull(slots(controller.state.value.detail))

            val cursor = controller.state.value.detail?.next_cursor?.toLongOrNull()
            assertNotNull(cursor, "the detail asks for another episode page")
            controller.detailPage(cursor!!)
            pageGate.complete(Unit)
            withTimeout(5000) { controller.state.first { it.detailLoadingOffset == null && it.detail?.episodes?.size == 4 } }
            stages += requireNotNull(slots(controller.state.value.detail))

            val titles = stages.map { it.title }
            val logos = stages.map { it.logo }
            val art = stages.map { it.heroArt }
            val meta = stages.map { it.metadataLine }
            val badgeLines = stages.map { it.badgeLine.joinToString("|") }
            val descriptions = stages.map { it.description }
            val casts = stages.map { it.cast }

            assertMonotonic("title", titles)
            assertMonotonic("logo", logos)
            assertMonotonic("hero art", art)
            assertMonotonic("metadata line", meta)
            assertMonotonic("rating badges", badgeLines)
            assertMonotonic("description", descriptions)
            assertMonotonic("cast", casts)

            assertEquals(tapped.title, stages.first().title, "the shell shows the card's title")
            assertNull(stages.first().logo, "the shell has no logo, so the title is drawn as text")
            assertEquals("https://detail/logo.png", stages.last().logo, "the real logo arrives once")
            assertEquals(tapped.poster, stages.first().heroArt, "the shell shows the card's poster as the hero")
            assertEquals("https://detail/backdrop.jpg", stages.last().heroArt, "the real backdrop replaces the poster thumbnail once")

            val metaLine = requireNotNull(stages.last().metadataLine)
            assertTrue("2025" in metaLine && "★ 8.1" in metaLine && "Comedy / Drama" in metaLine,
                "the card's richer metadata line survives the sparser detail: $metaLine")
            assertTrue(stages.last().badgeLine.contains("4K"), "the card's 4K badge survives")
            assertEquals("Lena, Marcus, Piper", stages.last().cast, "the detail's cast fills the blank slot once")
            assertEquals(fullDetail.item.description, stages.last().description, "the longer description wins once")

            assertEquals(listOf(0, 2, 4), stages.map { it.episodeCount }, "episodes only ever grow")
        } finally {
            detailGate.complete(Unit); pageGate.complete(Unit); fixture.close(controller)
        }
    }
}