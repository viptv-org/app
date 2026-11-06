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
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.heroStarring
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailHeroStarringLayoutsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val billed = listOf("Lena Ortiz", "Marcus Reid", "Piper Chan", "Sam Ito", "Rae Kim")
    private val description = "After a cosmic event lifts their street out of the suburbs, the Platt family learns what their neighbours were hiding."
    private val starringLine = "Starring Lena Ortiz, Marcus Reid, Piper Chan"

    private fun series(cast: List<String>) = Card(MediaIdentity("oak-street", "series", emptyList()), provider_id = "addon:21",
        title = "The End of Oak Street", badges = listOf("4K"),
        landscape = "https://images.example/oak-street.jpg", poster = "https://images.example/oak-street.jpg",
        description = description,
        metadata = CardMetadata(year = 2026, runtime_minutes = 44, genres = listOf("Drama", "Mystery"), rating = 8.0, cast = cast))
    private fun detail(cast: List<String>): DetailSnapshot {
        val card = series(cast)
        return DetailSnapshot(card, episodes = (1L..3L).map { number ->
            card.copy(identity = MediaIdentity("oak-street:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
                parent_identity = card.identity, title = "Episode $number", description = null,
                metadata = CardMetadata(runtime_minutes = 44))
        }, seasons = listOf(EpisodeSeason(1, 3, 0)))
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private inner class Page(val name: String, width: Int, height: Int, opening: DetailSnapshot, val tablet: Boolean = false,
        val browser: Boolean = false, val television: Boolean = false) : AutoCloseable {
        var detail by mutableStateOf(opening)
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

        fun nodes(): List<SemanticsNode> {
            val acc = mutableListOf<SemanticsNode>()
            fun visit(node: SemanticsNode) { acc += node; node.children.forEach(::visit) }
            scene.semanticsOwners.forEach { visit(it.rootSemanticsNode) }
            return acc
        }

        fun texts(): List<String> = nodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }
        fun node(text: String): SemanticsNode? = nodes().firstOrNull { text in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { part -> part.text } }
        override fun close() = scene.close()
    }

    private val layouts = listOf<Pair<String, (DetailSnapshot) -> Page>>(
        "phone" to { opening -> Page("phone", 390, 844, opening) },
        "tablet" to { opening -> Page("tablet", 1280, 800, opening, tablet = true) },
        "desktop" to { opening -> Page("desktop", 1280, 800, opening) },
        "browser" to { opening -> Page("browser", 1280, 900, opening, browser = true) },
        "tv" to { opening -> Page("tv", 1920, 1080, opening, television = true) },
    )

    @Test fun everyHeroDrawsTheTopThreeBilledNames() = onEdt {
        for ((name, layout) in layouts) layout(detail(billed)).use { page ->
            page.settle()
            val texts = page.texts()
            assertTrue(starringLine in texts, "$name: the hero names the top three: $texts")
            assertTrue(texts.none { "Sam Ito" in it || "Rae Kim" in it }, "$name: the line stops at three: $texts")
            val line = assertNotNull(page.node(starringLine), "$name: the line is drawn").boundsInRoot
            val description = assertNotNull(page.node(description), "$name: the description is drawn").boundsInRoot
            assertTrue(line.top >= description.bottom - 1f, "$name: the line follows the description: $line after $description")
            val gap = if (page.television) 60f else 40f
            assertTrue(line.top - description.bottom < gap,
                "$name: the line sits directly under it: ${line.top - description.bottom}px")
        }
    }

    @Test fun aTitleWithNoCastShowsNoStarringLine() = onEdt {
        for ((name, layout) in layouts) layout(detail(emptyList())).use { page ->
            page.settle()
            assertTrue(page.texts().none { it.startsWith("Starring") }, "$name: no cast, no line: ${page.texts()}")
        }
    }

    @Test fun theBillingOrderIsKeptAndBlankNamesAreDropped() {
        assertEquals("Lena Ortiz, Marcus Reid, Piper Chan", series(billed).heroStarring())
        assertEquals("Lena Ortiz, Marcus Reid", series(listOf("Lena Ortiz ", "", " Marcus Reid")).heroStarring())
        assertNull(series(emptyList()).heroStarring())
        assertNull(series(listOf("", "  ")).heroStarring(), "names that are only whitespace are not a line")
    }
}