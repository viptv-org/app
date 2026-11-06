package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvLiveFilterFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun channel() = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Fixture news", badges = emptyList()),
        id = "one", connectionId = "m3u:1", programs = listOf(LiveProgram("p1", "Morning news", 0, 180)))

    @Test fun tvGuideUpReachesTheGroupChip() {
        Page {
            LiveScreen(listOf(channel()), television = true, start = 0, now = 30, watch = {},
                groupLabels = mapOf("news" to "News", "sport" to "Sport"), groupChanged = {}, favoritesFilter = {})
        }.use { page ->
            page.focusGuide()
            page.press(Key.DirectionUp)
            assertEquals("Group: All", page.focusedChip(), "Up from the guide reaches the Group chip, not the guide")
        }
    }

    @Test fun tvGuideUpReachesTheFavouritesChipWhenItIsTheOnlyFilter() {
        Page {
            LiveScreen(listOf(channel()), television = true, start = 0, now = 30, watch = {},
                favoritesFilter = {})
        }.use { page ->
            page.focusGuide()
            page.press(Key.DirectionUp)
            assertEquals("Favourites", page.focusedText(), "Up from the guide reaches the only chip")
        }
    }

    @Test fun tvGuideUpReachesTheFavouriteActionThroughTheChips() {
        Page {
            LiveScreen(listOf(channel()), television = true, start = 0, now = 30, watch = {}, favoriteChanged = {},
                groupLabels = mapOf("news" to "News", "sport" to "Sport"), groupChanged = {}, favoritesFilter = {})
        }.use { page ->
            page.focusGuide()
            page.press(Key.DirectionUp)
            assertEquals("Group: All", page.focusedChip(), "Up from the guide reaches the chips")
            page.press(Key.DirectionUp)
            assertEquals("Favourite channel", page.focusedText(), "Up from the chips reaches the preview's favourite action")
            page.press(Key.DirectionRight)
            assertEquals("OK to watch", page.focusedText(), "Right from the favourite action reaches the watch tile")
            page.press(Key.DirectionDown)
            assertEquals("Group: All", page.focusedChip(), "Down from the preview returns to the chips")
            page.press(Key.DirectionDown)
            assertEquals("Morning news", page.focusedText(), "Down from the chips returns to the guide")
        }
    }

    @Test fun tvGuideUpReachesTheFavouriteActionWithoutChips() {
        Page {
            LiveScreen(listOf(channel()), television = true, start = 0, now = 30, watch = {}, favoriteChanged = {})
        }.use { page ->
            page.focusGuide()
            page.press(Key.DirectionUp)
            assertEquals("Favourite channel", page.focusedText(), "Up from the guide reaches the favourite action when no chip is drawn")
            page.press(Key.DirectionDown)
            assertEquals("Morning news", page.focusedText(), "Down returns to the guide")
        }
    }

    @Test fun tvChannelRowsReachTheFavouriteAction() {
        Page {
            LiveScreen(listOf(channel()), television = true, start = null, now = 30, watch = {}, favoriteChanged = {}, favoritesFilter = {})
        }.use { page ->
            page.until("the rows") { page.nodes().any { page.text(it).startsWith("Fixture news") } }
            page.until("the rows' focus") { page.focusedText()?.startsWith("Fixture news") == true }
            page.press(Key.DirectionUp)
            assertEquals("Favourites", page.focusedText(), "Up from the rows reaches the only chip")
            page.press(Key.DirectionUp)
            assertEquals("Favourite channel", page.focusedText(), "Up from the chips reaches the favourite action")
        }
    }

    private class Page(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        private var frame = 0L
        private val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                focusManager = LocalFocusManager.current
                Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
            }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close() } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text)
        fun focusedChip() = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Focused) == true && it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList
        }?.let(::text)

        fun until(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) { check(System.nanoTime() < deadline) { "timed out waiting for $what" }; frames(2) }
        }

        fun focusGuide() {
            until("the guide") { nodes().any { text(it) == "Morning news" } }
            until("the guide's focus") { focusedText() == "Morning news" }
        }

        // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
            if (!consumed && key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                focusManager!!.moveFocus(when (key) {
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionUp -> FocusDirection.Up
                    else -> FocusDirection.Down
                })
                frames()
            }
        }

        override fun close() = scene.close()
    }
}
