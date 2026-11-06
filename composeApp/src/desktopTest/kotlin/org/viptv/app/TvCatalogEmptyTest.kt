@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.CATALOG_CLEAR_ALL
import org.viptv.app.features.browse.CATALOG_FILTERED_TITLE
import org.viptv.app.features.browse.CATALOG_NO_TITLES_TITLE
import org.viptv.app.features.browse.TvBrowseScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Choice
import org.viptv.engine.wire.ControlKind
import org.viptv.engine.wire.QueryControl
import org.viptv.engine.wire.QueryValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// TV Movies/Series/Anime with nothing to show: the shared catalog state, the newest filter's Remove first, Clear all
// second, and no filter blame when nothing is filtered.
class TvCatalogEmptyTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun controls(peers: Int = 1) = DiscoveryControls(provider_id = "stremio", media_types = listOf("movie"), controls = emptyList(),
        catalogs = listOfNotNull(DiscoveryCatalog("popular", "Popular", "movie", connection_id = "c1", addon_id = 1,
            controls = listOf(QueryControl("genre", "Genre", ControlKind.MULTICHOICE, false,
                listOf(Choice("horror", "Horror"), Choice("crime", "Crime"), Choice("drama", "Drama"))))),
            DiscoveryCatalog("new", "New Releases", "movie", connection_id = "c1", addon_id = 1).takeIf { peers > 1 }),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Available"))

    private fun query(vararg genres: String) = BrowseQuery("movie", catalog = "popular", connection_id = "c1",
        values = genres.toList().takeIf { it.isNotEmpty() }?.let { listOf(QueryValue("genre", it)) }.orEmpty())

    private fun page(query: BrowseQuery): @Composable () -> Unit = {
        ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                TvBrowseScreen(emptyList(), busy = false, open = {}, controls = controls(), query = query,
                    filterChanged = { _, _ -> }, multipleChanged = { _, _ -> }, applyQuery = {}, retry = {})
            }
        }
    }

    private class Frame(content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private var focusManager: androidx.compose.ui.focus.FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            content()
            focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        }
        suspend fun settle(frames: Int = 40) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); yield() } }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(): List<String> = nodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        }
        fun has(text: String) = texts().any { it.contains(text) }
        fun action(label: String): SemanticsNode? = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        }
        fun focused(): String? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?.let { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } }
        suspend fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press drives the focus manager.
            if (!consumed) focusManager?.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
            delay(4); settle(8)
        }
        override fun close() = scene.close()
    }

    @Test fun anUnfilteredEmptyCatalogSaysNoTitlesYetAndClaimsNoFilters() = kotlinx.coroutines.runBlocking {
        val frame = Frame(page(query()))
        try {
            frame.settle()
            assertTrue(frame.has(CATALOG_NO_TITLES_TITLE), "an unfiltered empty catalog says there are no titles: ${frame.texts()}")
            assertFalse(frame.has("filters"), "nothing is filtered, so the page never blames filters: ${frame.texts()}")
            assertFalse(frame.action(CATALOG_CLEAR_ALL) != null, "Clear all is not offered when nothing is filtered")
            assertFalse(frame.action("Remove filter") != null)
            val focused = frame.focused()
            assertTrue(focused == null || focused.contains("Catalog:"), "a state with no action leaves the remote on the chip row: $focused")
        } finally { frame.close() }
    }

    @Test fun oneFilterPutsItsRemoveOnTheRemoteWithNoRedundantClear() = kotlinx.coroutines.runBlocking {
        val frame = Frame(page(query("horror")))
        try {
            frame.settle()
            assertTrue(frame.has(CATALOG_FILTERED_TITLE))
            assertTrue(frame.has("has no movies matching “Horror”."))
            val remove = frame.action("Remove “Horror”") ?: run { throw AssertionError("no Remove action: ${frame.texts()}") }
            assertTrue(remove.config.getOrNull(SemanticsProperties.Focused) == true, "the newest filter's Remove takes the remote")
            assertTrue(frame.action(CATALOG_CLEAR_ALL) == null,
                "one filter needs no redundant Clear all, the same as the phone and desktop page")
        } finally { frame.close() }
    }

    @Test fun severalFiltersNameTheCountAndOfferRemoveThenClearAll() = kotlinx.coroutines.runBlocking {
        val frame = Frame(page(query("horror", "crime", "drama")))
        try {
            frame.settle()
            assertTrue(frame.has("has no movies with all 3 filters. Removing the newest one usually helps."), "${frame.texts()}")
            val remove = frame.action("Remove “Drama”") ?: run { throw AssertionError("the newest filter is the one offered") }
            val clear = frame.action(CATALOG_CLEAR_ALL) ?: run { throw AssertionError("no Clear all action: ${frame.texts()}") }
            assertTrue(remove.config.getOrNull(SemanticsProperties.Focused) == true, "Remove takes the remote")
            assertTrue(remove.boundsInRoot.left < clear.boundsInRoot.left, "Remove reads before Clear all")
            frame.press(Key.DirectionRight)
            assertTrue(frame.action(CATALOG_CLEAR_ALL)?.config?.getOrNull(SemanticsProperties.Focused) == true,
                "Right reaches Clear all (focused: ${frame.focused()})")
        } finally { frame.close() }
    }

    @Test fun render() = kotlinx.coroutines.runBlocking {
        val dir = System.getenv("VIPTV_1039_RENDER_DIR")?.let(::File)?.also { it.deleteRecursively(); it.mkdirs() }
            ?: return@runBlocking
        for ((name, q) in listOf("no-filters" to query(), "one-filter" to query("horror"),
            "three-filters" to query("horror", "crime", "drama"))) {
            val frame = Frame(page(q))
            try {
                frame.settle()
                frame.scene.render(1_000_000_000L).use { image ->
                    File(dir, "tv-$name.png").writeBytes(image.encodeToData()!!.bytes)
                }
            } finally { frame.close() }
        }
    }
}