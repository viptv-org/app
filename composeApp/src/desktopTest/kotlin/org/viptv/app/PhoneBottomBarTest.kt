package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.components.FocusLazyVerticalGrid
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.LocalPhoneScroll
import org.viptv.app.components.PhoneBottomBar
import org.viptv.app.components.PhoneScroll
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhoneBottomBarTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun theBarHidesOnScrollDownAndShowsOnASmallScrollUp() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        assertFalse(scroll.barHidden)
        scroll.onPosition(true, 400f)
        assertTrue(scroll.barHidden, "scrolling down hides the bar")
        scroll.onPosition(true, 360f)
        assertTrue(scroll.barHidden, "40 dp up is below the reveal threshold")
        scroll.onPosition(true, 352f)
        assertFalse(scroll.barHidden, "48 dp up brings the bar back")
        scroll.onPosition(true, 500f)
        assertTrue(scroll.barHidden, "scrolling down again hides it")
        scroll.onPosition(false, 0f)
        assertFalse(scroll.barHidden, "reaching the top always shows it")
    }

    @Test fun scrollingAPhoneGridDrivesTheBar() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        val scene = scene(scroll) { scroll.barHidden }
        try {
            frames(scene)
            assertFalse(scroll.barHidden, "the bar starts visible at the top")
            scrollTo(scene, 900f); frames(scene, 24)
            assertTrue(scroll.barHidden, "scrolling down hides the bar")
            scrollTo(scene, -48f); frames(scene, 24)
            assertFalse(scroll.barHidden, "scrolling up 48 dp shows the bar again")
        } finally { scene.close() }
    }

    @Test fun hidingTheBarNeverMovesTheContent() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        var hidden by mutableStateOf(false)
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalPhoneScroll provides scroll,
                    LocalBottomBarFades provides true, LocalBottomClearance provides 80.dp) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            FocusLazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
                                items(90) { Box(Modifier.fillMaxWidth().height(160.dp).background(Palette.raised)) }
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            PhoneBottomBar(Screen.Home, {}, hidden = hidden)
                        }
                    }
                }
            }
        }
        try {
            frames(scene)
            val gridTop = gridNode(scene).boundsInRoot.top
            hidden = true; frames(scene, 8)
            assertEquals(gridTop, gridNode(scene).boundsInRoot.top, 1f, "hiding the bar must not move the page")
            hidden = false; frames(scene, 8)
            assertEquals(gridTop, gridNode(scene).boundsInRoot.top, 1f, "showing the bar must not move the page")
        } finally { scene.close() }
    }

    @Test fun capturesTheBarForThePr() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        val scene = scene(scroll) { scroll.barHidden }
        try {
            frames(scene); capture(scene, "bar-visible-top")
            scrollTo(scene, 900f); frames(scene, 24); capture(scene, "bar-hidden")
            scrollTo(scene, -48f); frames(scene, 24); capture(scene, "bar-visible-again")
        } finally { scene.close() }
    }

    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_BOTTOM_BAR_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }

    private fun scene(scroll: PhoneScroll, hiddenProvider: () -> Boolean): ImageComposeScene {
        frame = 0
        return ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalPhoneScroll provides scroll,
                    LocalBottomBarFades provides true, LocalBottomClearance provides 80.dp) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            FocusLazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
                                items(90) { Box(Modifier.fillMaxWidth().height(160.dp).background(Palette.raised)) }
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            PhoneBottomBar(Screen.Home, {}, hidden = hiddenProvider())
                        }
                    }
                }
            }
        }
    }

    private fun scrollTo(scene: ImageComposeScene, delta: Float) {
        gridNode(scene).config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta)
    }

    private fun gridNode(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        .maxByOrNull { it.boundsInRoot.height } ?: fail("no scrolling page")

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}