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
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsHeroStabilityTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val show = "tt0903747"
    private val metadata = CardMetadata(year = 2008, runtime_minutes = 47, genres = listOf("Crime", "Drama"), rating = 9.5)
    private val description = "A chemistry teacher turns to a life of crime to provide for his family."

    private fun card(logo: String? = null, art: String? = "https://images.example/back.jpg") = Card(
        MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Breaking Bad", badges = emptyList(), metadata = metadata, description = description,
        logo = logo, poster = art, landscape = art)

    private fun full(logo: String? = null) = DetailSnapshot(
        item = card(logo = logo), episodes = listOf(episode(1)),
        recommendations = listOf(card(logo = "https://images.example/bcs.png").copy(
            identity = MediaIdentity("tt2085059", "series", listOf(ExternalId("imdb", "tt2085059"))), title = "Better Call Saul")))

    private fun episode(number: Long) = card().copy(
        identity = MediaIdentity("$show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = card().identity, title = "Episode $number")

    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun textOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun ImageComposeScene.topOf(text: String): Float? = nodes().firstOrNull { textOf(it) == text }?.boundsInRoot?.top
    private fun ImageComposeScene.loadingSkeleton() = nodes().any {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { label -> label == "Loading title details" } }

    private inner class Details(shell: DetailSnapshot) : AutoCloseable {
        var detail by mutableStateOf(shell)
        var busy by mutableStateOf(true)
        private var frame = 0L
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy, error = null, back = {}, sources = {})
            }
        } }
        fun render(frames: Int = 8) = repeat(frames) { scene.render(frame++ * 16_666_667L).close() }
        override fun close() = scene.close()
    }

    @Test fun artworkAndLogoArrivingKeepTheHeroActionsPut() {
        Details(DetailSnapshot(card(logo = null, art = null), episodes = emptyList())).use { page ->
            page.render()
            val playTop = assertNotNull(page.scene.topOf("Play"), "the hero Play is on the loading frame").let { it }
            val saveTop = assertNotNull(page.scene.topOf("My List"), "the hero My List is on the loading frame")

            page.busy = false
            page.detail = full(logo = "https://images.example/logo.png")
            page.render(24)

            assertEquals(playTop, page.scene.topOf("Play"), "artwork and logo arriving do not move Play")
            assertEquals(saveTop, page.scene.topOf("My List"), "artwork and logo arriving do not move My List")
            assertFalse(page.scene.loadingSkeleton(), "the below-hero skeleton is gone once the meta arrives")
        }
    }

    @Test fun aLogoArrivingKeepsTheActionsAndBelowContentPut() {
        Details(DetailSnapshot(card(logo = null), episodes = emptyList())).use { page ->
            page.render()
            val playTop = assertNotNull(page.scene.topOf("Play"))

            page.busy = false
            page.detail = full(logo = "https://images.example/logo.png")
            page.render(24)

            assertEquals(playTop, page.scene.topOf("Play"), "a logo arriving later does not move the hero actions")
        }
    }
}