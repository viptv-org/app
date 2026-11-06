@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.home.HOME_FAILED_TITLE
import org.viptv.app.features.home.HOME_NO_CATALOGS_ACTION
import org.viptv.app.features.home.HOME_NO_CATALOGS_MESSAGE
import org.viptv.app.features.home.HOME_NO_CATALOGS_TITLE
import org.viptv.app.features.home.HOME_NO_TITLES_MESSAGE
import org.viptv.app.features.home.HOME_NO_TITLES_TITLE
import org.viptv.app.features.home.HOME_RETRY_ACTION
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// One page state block on every layout: Home draws the same centred icon, headline, help line and action at 390, tablet,
// desktop, web and 1920, and a failed action never replaces the hero and rows.
class HomePageStatesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private data class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false,
        val tablet: Boolean = false, val browser: Boolean = false, val desktopChrome: Boolean = false)

    private val layouts = listOf(
        Layout("phone", 390, 844),
        Layout("tablet", 834, 1112, tablet = true),
        Layout("desktop", 1280, 800, desktopChrome = true),
        Layout("web", 1440, 900, browser = true),
        Layout("tv", 1920, 1080, television = true))

    private fun card(id: String) = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "addon:1",
        title = "Title $id", badges = emptyList())

    private fun row(state: RowState = RowState.READY, cards: List<Card> = listOf(card("row-1"), card("row-2"))) =
        HomeRow(id = "movies", title = "Movies", provider_id = "addon:1", state = state, cards = cards)

    private fun home(hero: Card? = null, rows: List<HomeRow> = emptyList(), complete: Boolean = true) =
        HomeSnapshot(Scope("viptv:remote", "account", "1", 1), 1, hero = hero, rows = rows, complete = complete)

    private val content = home(hero = card("hero"), rows = listOf(row()))

    private enum class Case { NoCatalogs, NoTitles, FailedFirstLoad, FailedAction }

    private val cases = listOf(Case.NoCatalogs, Case.NoTitles, Case.FailedFirstLoad, Case.FailedAction)

    private fun page(case: Case, layout: Layout): @Composable () -> Unit = {
        ViptvTheme(layout.television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                when (case) {
                    Case.NoCatalogs -> HomeScreen(home(), layout.television, open = {}, retry = {}, busy = false)
                    Case.NoTitles -> HomeScreen(home(rows = listOf(row(state = RowState.EMPTY, cards = emptyList()))),
                        layout.television, open = {}, retry = {}, busy = false)
                    Case.FailedFirstLoad -> HomeScreen(null, layout.television, open = {}, retry = {}, busy = false,
                        error = "The connection dropped.")
                    Case.FailedAction -> HomeScreen(content, layout.television, open = {}, retry = {}, busy = false,
                        error = "The connection dropped.")
                }
            }
        }
    }

    private class Frame(val width: Int, val height: Int, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(): List<String> = nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        }
        fun shows(text: String) = texts().any { it.contains(text) }
        fun node(text: String): SemanticsNode? = nodes().firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(text) } == true
        }
        override fun close() = scene.close()
    }

    @Test fun everyLayoutDrawsTheSameBlockForHomeWithNothingToShow() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) for (case in listOf(Case.NoCatalogs, Case.NoTitles, Case.FailedFirstLoad)) {
            val frame = Frame(layout.width, layout.height, page(case, layout))
            try {
                frame.settle()
                val title = when (case) {
                    Case.NoCatalogs -> HOME_NO_CATALOGS_TITLE
                    Case.NoTitles -> HOME_NO_TITLES_TITLE
                    else -> HOME_FAILED_TITLE
                }
                val body = when (case) {
                    Case.NoCatalogs -> HOME_NO_CATALOGS_MESSAGE
                    Case.NoTitles -> HOME_NO_TITLES_MESSAGE
                    else -> "The connection dropped."
                }
                val action = if (case == Case.NoCatalogs) HOME_NO_CATALOGS_ACTION else HOME_RETRY_ACTION
                val label = "${layout.name}/${case.name}"
                if (!frame.shows(title)) failures += "$label: '$title' is not on the page"
                if (!frame.shows(body)) failures += "$label: '$body' is not on the page"
                if (!frame.shows(action)) failures += "$label: the '$action' action is not on the page"
                val node = frame.node(title)
                if (node == null) { failures += "$label: no title node"; continue }
                val bounds = node.boundsInRoot
                val centre = bounds.left + bounds.width / 2
                if (abs(centre - layout.width / 2f) > 2f) failures += "$label: the block is off-centre (centre $centre of ${layout.width})"
                if (layout.television) {
                    val middle = bounds.top + bounds.height / 2
                    if (abs(middle - layout.height / 2f) > 90f) failures += "$label: the TV block is not centred in the page ($middle of ${layout.height})"
                } else if (bounds.top < 60f) failures += "$label: the block sits at the page's top edge (${bounds.top})"
                if (frame.shows("Could not load Home") || frame.shows("No catalogs configured") || frame.shows("Retry Home") ||
                    frame.shows("Nothing to show yet")) failures += "$label: an old Home string is still drawn"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "Home's page state on every layout")
    }

    @Test fun thePageStateIsNotACardOnEveryLayout() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) {
            val frame = Frame(layout.width, layout.height, page(Case.FailedFirstLoad, layout))
            try {
                frame.settle()
                val node = frame.node(HOME_FAILED_TITLE)
                if (node == null) { failures += "${layout.name}: no title"; continue }
                val bounds = node.boundsInRoot
                val image = frame.scene.render(1_000_000_000L)
                val pixels = image.toComposeImageBitmap().toPixelMap()
                image.close()
                val y = (bounds.top - 4).toInt()
                for (dx in listOf(-100, -60, 60, 100)) {
                    val x = (bounds.left + bounds.width / 2 + dx).toInt()
                    if (x < 0 || x >= pixels.width || y < 0 || y >= pixels.height) continue
                    if (pixels[x, y] != Palette.background) failures += "${layout.name}: the block draws a surface at x $x ($dx from the title)"
                }
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "the page state block is on the page ground, not on a card")
    }

    @Test fun aFailedActionKeepsTheHeroAndRowsOnEveryLayout() = kotlinx.coroutines.runBlocking {
        val failures = mutableListOf<String>()
        for (layout in layouts) {
            val frame = Frame(layout.width, layout.height, page(Case.FailedAction, layout))
            try {
                frame.settle()
                if (!frame.shows("Title hero")) failures += "${layout.name}: the hero left the page"
                if (!frame.shows("Title row-1")) failures += "${layout.name}: the rows left the page"
                if (frame.shows(HOME_FAILED_TITLE)) failures += "${layout.name}: the failed action became the page state"
            } finally { frame.close() }
        }
        assertEquals(emptyList(), failures, "a failed action leaves Home's content in place")
    }

    @Test fun render() = kotlinx.coroutines.runBlocking {
        val dir = System.getenv("VIPTV_1036_RENDER_DIR")?.let(::File)?.also { it.deleteRecursively(); it.mkdirs() }
            ?: return@runBlocking
        for (layout in layouts) for (case in cases) {
            val frame = Frame(layout.width, layout.height, page(case, layout))
            try {
                frame.settle()
                frame.scene.render(1_000_000_000L).use { image ->
                    File(dir, "${layout.name}-${case.name}.png").writeBytes(image.encodeToData()!!.bytes)
                }
            } finally { frame.close() }
        }
    }
}