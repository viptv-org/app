@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.features.sources.SOURCE_MENU_PANE
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate
import org.viptv.engine.wire.SourcePresentation
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

class TvMenuMetricsTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val source = SourceCandidate("s1", "addon:1", "Torrentio 4K", detail = "[Torrentio] 4K", quality_height = 2160,
        presentation = SourcePresentation(provider_name = "Torrentio", source_id = "Torrentio"))
    private val card = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl", title = "Cached title",
        subtitle = "1h 52m left", badges = listOf("4K"), poster = null)

    private class Scene(width: Int, height: Int, television: Boolean, pointer: Boolean = false, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television, pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        fun frames(count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
        private fun label(node: SemanticsNode): String =
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ").trim()
        fun item(name: String) = nodes().firstOrNull {
            label(it) == name && it.config.contains(SemanticsActions.OnClick)
        }
        fun pane(title: String) = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.PaneTitle) == title }
        fun titled(text: String) = nodes().firstOrNull { label(it) == text }
        fun keyDown(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); frames(2) }
        fun keyUp(key: Key) { scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)); frames(2) }
        suspend fun waitFor(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 6_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "timed out waiting for $what" }
                frames(2); delay(15)
            }
        }
        fun capture(dir: String, name: String) {
            java.io.File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }

    private fun sourceMenu(width: Int, height: Int, television: Boolean, pointer: Boolean = false) = Scene(width, height, television, pointer) {
        SourcesScreen(null, listOf(source), false, null, {}, {}, television = television)
    }

    private fun titleMenu(width: Int, height: Int, scale: Float, requester: FocusRequester) = Scene(width, height, television = true) {
        CompositionLocalProvider(LocalCardActions provides actions) {
            Box(Modifier.fillMaxSize().padding(60.dp)) { TvCatalogCard(card, scale, true, Modifier.focusRequester(requester), focused = {}, open = {}) }
        }
    }

    private suspend fun Scene.openTitleMenu(requester: FocusRequester) {
        frames(30)
        assertTrue(requester.requestFocus(), "the card takes focus")
        frames()
        keyDown(Key.DirectionCenter)
        waitFor("the title menu") { titled("Cached title actions") != null }
        keyUp(Key.DirectionCenter)
        frames(30)
    }

    private val actions = CardActions(play = {}, toggleSave = {}, isSaved = { false }, chooseSource = {}, playFromStart = {}, markWatched = {})

    private fun assertNear(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) <= 2f, "$what expected ~$expected, was $actual")

    @Test fun theSourceMenuDrawsAtTheTvMetricsOn1920() = runBlocking {
        sourceMenu(1920, 1080, television = true).use { scene ->
            scene.frames(20)
            scene.keyDown(Key.DirectionCenter)
            scene.waitFor("the source menu") { scene.pane(SOURCE_MENU_PANE) != null }
            scene.keyUp(Key.DirectionCenter)
            scene.frames(30)
            assertNear(600f, scene.pane(SOURCE_MENU_PANE)!!.boundsInRoot.width, "menu width")
            assertNear(72f, scene.item("Play this source")!!.boundsInRoot.height, "menu row height")
        }
    }

    @Test fun theSourceMenuScalesWithTheCanvasOn1024() = runBlocking {
        val scale = 1024f / 1920f
        sourceMenu(1024, 576, television = true).use { scene ->
            scene.frames(20)
            scene.keyDown(Key.DirectionCenter)
            scene.waitFor("the source menu") { scene.pane(SOURCE_MENU_PANE) != null }
            scene.keyUp(Key.DirectionCenter)
            scene.frames(30)
            assertNear(600 * scale, scene.pane(SOURCE_MENU_PANE)!!.boundsInRoot.width, "menu width at 1024")
            assertNear(maxOf(72 * scale, 44f), scene.item("Play this source")!!.boundsInRoot.height, "menu row height at 1024")
        }
    }

    @Test fun theTitleMenuDrawsAtTheTvMetricsOn1920() = runBlocking {
        val requester = FocusRequester()
        titleMenu(1920, 1080, scale = 1f, requester).use { scene ->
            scene.openTitleMenu(requester)
            assertNear(600f, scene.titled("Cached title actions")!!.boundsInRoot.width, "title menu width")
            assertNear(72f, scene.item("Play")!!.boundsInRoot.height, "title menu row height")
        }
    }

    @Test fun theTitleMenuScalesWithTheCanvasOn1024() = runBlocking {
        val scale = 1024f / 1920f
        val requester = FocusRequester()
        titleMenu(1024, 576, scale, requester).use { scene ->
            scene.openTitleMenu(requester)
            assertNear(600 * scale, scene.titled("Cached title actions")!!.boundsInRoot.width, "title menu width at 1024")
            assertNear(maxOf(72 * scale, 44f), scene.item("Play")!!.boundsInRoot.height, "title menu row height at 1024")
        }
    }

    @Test fun thePointerMenuKeepsItsOwnGeometry() = runBlocking {
        sourceMenu(1280, 800, television = false, pointer = true).use { scene ->
            scene.frames(20)
            assertTrue(scene.item("More actions")!!.config[SemanticsActions.OnClick].action!!.invoke())
            scene.frames(30)
            assertNear(300f, scene.pane(SOURCE_MENU_PANE)!!.boundsInRoot.width, "pointer menu width")
            assertNear(44f, scene.item("Play this source")!!.boundsInRoot.height, "pointer menu row height")
        }
    }

    @Test fun rendersForEvidence(): Unit = runBlocking {
        val dir = System.getenv("VIPTV_TV_MENU_CAPTURE_DIR") ?: return@runBlocking
        sourceMenu(1920, 1080, television = true).use { scene ->
            scene.frames(20); scene.keyDown(Key.DirectionCenter)
            scene.waitFor("the source menu") { scene.pane(SOURCE_MENU_PANE) != null }
            scene.keyUp(Key.DirectionCenter); scene.frames(40)
            scene.capture(dir, "source-menu-tv-1920")
        }
        sourceMenu(1024, 576, television = true).use { scene ->
            scene.frames(20); scene.keyDown(Key.DirectionCenter)
            scene.waitFor("the source menu") { scene.pane(SOURCE_MENU_PANE) != null }
            scene.keyUp(Key.DirectionCenter); scene.frames(40)
            scene.capture(dir, "source-menu-tv-1024")
        }
        val wide = FocusRequester()
        titleMenu(1920, 1080, 1f, wide).use { scene -> scene.openTitleMenu(wide); scene.frames(20); scene.capture(dir, "title-menu-tv-1920") }
        val small = FocusRequester()
        titleMenu(1024, 576, 1024f / 1920f, small).use { scene -> scene.openTitleMenu(small); scene.frames(20); scene.capture(dir, "title-menu-tv-1024") }
    }
}