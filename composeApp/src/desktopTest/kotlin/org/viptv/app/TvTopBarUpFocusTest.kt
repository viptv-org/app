package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.components.TvNavigation
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvTopBarUpFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private var focusManager: FocusManager? = null
    private var frame = 0L
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }
    // Desktop maps no arrow key to a focus move, so an unconsumed D-pad press has to drive the focus manager.
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(FocusDirection.Up)
        settle(scene)
    }
    private suspend fun focusContent(scene: ImageComposeScene) {
        val node = nodes(scene).firstOrNull { text(it).contains(contentLabel) && it.config.contains(SemanticsActions.RequestFocus) }
            ?: error("The page has no content to focus")
        assertTrue(node.config[SemanticsActions.RequestFocus].action?.invoke() == true, "Content refused focus")
        settle(scene)
        assertEquals(contentLabel, focused(scene)?.let(::text), "Content did not take focus")
    }
    private fun scene(screen: Screen) = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
        focusManager = LocalFocusManager.current
        Box(Modifier.fillMaxSize().background(Palette.background)) {
            Column(Modifier.fillMaxSize()) {
                TvNavigation(screen, null) {}
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    Button(onClick = {}, modifier = Modifier.padding(start = 96.dp, bottom = 40.dp)) { Text(contentLabel) }
                }
            }
        }
    } }

    private val rails = listOf(Screen.Home to "Home", Screen.Movies to "Movies", Screen.Series to "Series", Screen.Anime to "Anime",
        Screen.Live to "Live", Screen.Calendar to "Calendar", Screen.Library to "My List")

    @Test fun upFromContentOnEveryPageReturnsToTheSelectedRail() = onEdt {
        for ((screen, label) in rails) {
            val scene = scene(screen)
            try {
                settle(scene)
                focusContent(scene)
                press(scene, Key.DirectionUp)
                val landed = requireNotNull(focused(scene)) { "${screen.name}: Up from content left nothing focused" }
                assertTrue(text(landed).contains(label), "Up from content on ${screen.name} must focus its '$label' tab, got '${text(landed)}'")
            } finally { scene.close() }
        }
    }

    @Test fun upFromContentOnAPageWithoutARailStillEntersTheBar() = onEdt {
        val scene = scene(Screen.History)
        try {
            settle(scene)
            focusContent(scene)
            press(scene, Key.DirectionUp)
            val landed = requireNotNull(focused(scene)) { "Up from content left nothing focused" }
            assertTrue(text(landed).isNotBlank(), "Up from content on History must still reach the top bar, got '${text(landed)}'")
        } finally { scene.close() }
    }

    private companion object { const val contentLabel = "Content item" }
}