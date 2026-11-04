package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.components.heroMetadataParts
import org.viptv.app.connection.SourceGroups
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.state.DetailSourcePreview
import org.viptv.app.state.withCardPresentation
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsStagedRenderTest {
    @get:Rule val uiThread = UiThreadRule()

    private val identity = MediaIdentity("tt1856010", "series", listOf(ExternalId("imdb", "tt1856010")))
    private val series = Card(identity, provider_id = "addon:21", title = "Gabby & Friends", badges = emptyList())
    private fun episode(number: Long) = series.copy(
        identity = MediaIdentity("tt1856010:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = identity, title = "Episode $number")

    private val shortDescription = "A short card description."
    private val longDescription = "A much longer description that arrives with the full read and must win."
    private val metaLine = series.copy(metadata = CardMetadata(year = 2025, runtime_minutes = 30,
        genres = listOf("Comedy", "Drama"), rating = 8.1, rating_votes = 1200, certification = "TV-Y7")).heroMetadataParts().joinToString(" · ")
    private val castLine = "Starring Lena, Marcus, Piper"

    private val shell = DetailSnapshot(series.copy(description = shortDescription, poster = "https://card/poster.jpg",
        metadata = CardMetadata(year = 2025, runtime_minutes = 30, genres = listOf("Comedy", "Drama"),
            rating = 8.1, rating_votes = 1200, certification = "TV-Y7", cast = emptyList()), badges = listOf("4K")),
        episodes = emptyList())

    private val full = DetailSnapshot(series.copy(description = longDescription, poster = "https://detail/poster-full.jpg",
        landscape = "https://detail/backdrop.jpg", logo = "https://detail/logo.png",
        metadata = CardMetadata(cast = listOf("Lena", "Marcus", "Piper")),
        badges = listOf("4K", "HDR", "CC")),
        episodes = (1L..2L).map { episode(it) }, seasons = listOf(EpisodeSeason(1, 4, 0)))

    private val preview = DetailSourcePreview(
        rows = listOf(SourceCandidate("s1", "addon:1", "1080p", quality_height = 1080,
            presentation = SourcePresentation(provider_name = "Torrentio", quality = "1080p"))),
        groups = SourceGroups(bestMatches = listOf("s1"), bestMatchId = "s1"), settled = true)

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun ImageComposeScene.shows(value: String) = nodes(this).any { node ->
        text(node) == value || node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == value } }
    private fun ImageComposeScene.mentions(value: String) = nodes(this).any { node ->
        text(node).contains(value) || node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains(value) } }
    private fun ImageComposeScene.hasText(value: String) = nodes(this).any { text(it) == value }
    private fun ImageComposeScene.loadingSkeleton() = nodes(this).any {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { label -> label == "Loading title details" } }

    private inner class Details(val television: Boolean) : AutoCloseable {
        var detail by mutableStateOf<DetailSnapshot?>(shell)
        var busy by mutableStateOf(true)
        var sourcePreview by mutableStateOf<DetailSourcePreview?>(null)
        private var frame = 0L
        val width = if (television) 1920 else 390
        val height = if (television) 1080 else 844
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy, error = null, back = {}, sources = {}, television = television, sourcePreview = sourcePreview)
            }
        } }
        fun render(frames: Int = 8) = repeat(frames) { scene.render(frame++ * 16_666_667L).close() }
        fun renderUntil(maxFrames: Int = 60, condition: () -> Boolean) {
            repeat(maxFrames) {
                scene.render(frame++ * 16_666_667L).close()
                if (condition()) return
            }
        }
        override fun close() = scene.close()
    }

    @Test fun phoneSlotsOnlyFillAndImprove() {
        Details(false).use { page ->
            page.render()
            assertTrue(page.scene.hasText("Gabby & Friends"), "the shell draws the title")
            assertTrue(page.scene.shows(metaLine), "the shell shows the card's full metadata line")
            assertTrue(page.scene.shows(shortDescription), "the shell shows the card's short description")
            assertTrue(page.scene.loadingSkeleton(), "the meta-only parts are skeletons during the read")

            page.detail = full.withCardPresentation(shell.item); page.busy = false
            page.renderUntil { page.scene.hasText("Gabby & Friends") && page.scene.shows(longDescription) &&
                page.scene.shows(castLine) && page.scene.shows("1. Episode 1") && !page.scene.loadingSkeleton() }

            assertTrue(page.scene.hasText("Gabby & Friends"), "the title slot never blanks")
            assertTrue(page.scene.shows(metaLine), "the metadata line is not downgraded by the sparser detail")
            assertTrue(page.scene.shows(longDescription), "the longer description replaces the shorter one")
            assertTrue(page.scene.shows(castLine), "the cast fills in once")
            assertTrue(page.scene.shows("1. Episode 1"), "the episode list replaces the skeleton")
            assertFalse(page.scene.loadingSkeleton(), "the skeleton is gone")

            page.sourcePreview = preview
            page.renderUntil { page.scene.mentions("Torrentio · 1 source") }
            assertTrue(page.scene.mentions("Torrentio · 1 source"), "the source summary fills in once")
        }
    }

    @Test fun tvSlotsOnlyFillAndImprove() {
        Details(true).use { page ->
            page.render()
            assertTrue(page.scene.hasText("Gabby & Friends"), "the TV shell draws the title")

            page.detail = full.withCardPresentation(shell.item); page.busy = false
            page.renderUntil { page.scene.hasText("Gabby & Friends") && page.scene.shows(longDescription) &&
                page.scene.shows(castLine) && !page.scene.loadingSkeleton() }

            assertTrue(page.scene.hasText("Gabby & Friends"), "the TV title slot never blanks")
            assertTrue(page.scene.shows(longDescription), "the TV description fills in")
            assertTrue(page.scene.shows(castLine), "the TV cast fills in")
            assertFalse(page.scene.loadingSkeleton(), "the TV skeleton is gone")
        }
    }
}