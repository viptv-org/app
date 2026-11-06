package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.RELATED_HEADING
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailRelatedHeadingTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val movie = Card(MediaIdentity("oak-street", "movie", emptyList()), provider_id = "addon:21",
        title = "The End of Oak Street", badges = emptyList(),
        landscape = "https://images.example/oak-street.jpg", poster = "https://images.example/oak-street.jpg",
        description = "After a cosmic event lifts their street out of the suburbs, the Platt family learns what their neighbours were hiding.",
        metadata = CardMetadata(year = 2026, runtime_minutes = 100, genres = listOf("Drama"), rating = 8.0))
    private val detail = DetailSnapshot(movie, episodes = emptyList(),
        recommendations = listOf(movie.copy(identity = MediaIdentity("obsession", "movie", emptyList()), title = "Obsession")))

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(width: Int, height: Int, val tablet: Boolean = false, val browser: Boolean = false,
        val television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(this@DetailRelatedHeadingTest.detail)
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                    tablet = tablet, browser = browser, continuing = emptyList())
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun texts(): List<String> {
            val acc = mutableListOf<String>()
            fun visit(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().forEach { acc += it.text }
                node.children.forEach(::visit)
            }
            scene.semanticsOwners.forEach { visit(it.rootSemanticsNode) }
            return acc
        }
        override fun close() = scene.close()
    }

    private val layouts = listOf<Pair<String, () -> Page>>(
        "phone" to { Page(390, 844) },
        "tablet" to { Page(1280, 800, tablet = true) },
        "desktop" to { Page(1280, 800) },
        "browser" to { Page(1280, 900, browser = true) },
        "tv" to { Page(1920, 1080, television = true) },
    )

    @Test fun everyLayoutUsesTheRecordedHeading() = onEdt {
        for ((name, layout) in layouts) layout().use { page ->
            page.settle()
            val texts = page.texts()
            assertTrue(RELATED_HEADING in texts, "$name: \"$RELATED_HEADING\" is drawn: $texts")
            assertEquals(emptyList(), texts.filter { it.equals(RELATED_HEADING, ignoreCase = true) && it != RELATED_HEADING },
                "$name: no other casing of the heading is drawn")
        }
    }

    @Test fun noScreenWritesTheHeadingOutInAnotherCasing() {
        val root = File("src/commonMain/kotlin/org/viptv/app")
        assertTrue(root.isDirectory, "run from the composeApp module")
        val retired = Regex("More Like This|More like This|more like this")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().withIndex().filter { retired.containsMatchIn(it.value) && "RELATED_HEADING" !in it.value }
                .map { "${file.name}:${it.index + 1}" } }
            .toList()
        assertEquals(emptyList(), found, "the related heading is drawn from RELATED_HEADING")
    }
}