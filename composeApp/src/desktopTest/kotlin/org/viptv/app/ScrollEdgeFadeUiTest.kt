package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.BottomBarHeight
import org.viptv.app.components.BottomNavigation
import org.viptv.app.components.FocusLazyVerticalGrid
import org.viptv.app.components.LocalBarsFadeContent
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.ScrollEdgeFadeHeight
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ScrollEdgeFadeUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val fade = ScrollEdgeFadeHeight.value.roundToInt()
    private val barTop = HEIGHT - BottomBarHeight.value.roundToInt()
    private val background = Palette.background.toArgb()
    private val raised = Palette.raised.toArgb()

    @Test fun contentScrolledUnderTheTopBarFadesIntoTheBackground() {
        for (barsFade in listOf(true, false)) {
            var barBottom = 0f
            val scene = phone(barsFade, bottomBar = false) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().onGloballyPositioned { barBottom = it.boundsInRoot().bottom }) {
                        PhoneHeader(Screen.Library, null, {})
                    }
                    page(Modifier.weight(1f))
                }
            }
            try {
                frames(scene)
                val column = readColumn(scene, 20, barBottom.roundToInt() + 1, fade)
                if (barsFade) {
                    assertNear(background, column.first(), "under the top bar: the content meets the background")
                    assertNear(raised, column.last(), "under the top bar: the content is back at its own colour")
                    assertNoSteps(column, "under the top bar")
                } else assertTrue(column.all { distance(it, raised) <= 1 },
                    "a page with no bar over it keeps its hard edge: ${column.map(::hex)}")
            } finally { scene.close() }
        }
    }

    @Test fun contentUnderTheBottomBarFadesIntoTheBackground() {
        val scene = phone(barsFade = true, bottomBar = true) {
            page(Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(Screen.Home, {}) }
        }
        try {
            frames(scene)
            val column = readColumn(scene, 20, barTop - fade, fade - 2)
            assertNear(raised, column.first(), "under the bottom bar: the fade starts at the content colour")
            assertNoSteps(column, "under the bottom bar")
            assertTrue(distance(column.last(), raised) >= distance(raised, background) * 2 / 3,
                "the content is well faded where it meets the bar: ${column.map(::hex)}")
        } finally { scene.close() }
    }

    @Test fun theBottomBarStopsFadingOnceTheListEnds() {
        val scene = phone(barsFade = true, bottomBar = true) {
            page(Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter)) { BottomNavigation(Screen.Home, {}) }
        }
        try {
            frames(scene)
            scrollToEnd(scene)
            val column = readColumn(scene, 20, barTop - fade, fade - 2)
            assertTrue(column.all { distance(it, raised) <= 1 },
                "with nothing left below, the last row keeps its colour: ${column.map(::hex)}")
        } finally { scene.close() }
    }

    @Test fun barsFadeContentOnlyWhereABarCoversIt() {
        assertTrue(barsFadeContent(television = false, desktopChrome = false, touch = true, width = 393.dp))
        assertTrue(barsFadeContent(television = false, desktopChrome = false, touch = true, width = 1024.dp))
        assertTrue(barsFadeContent(television = false, desktopChrome = false, touch = false, width = 900.dp))
        assertFalse(barsFadeContent(television = false, desktopChrome = false, touch = false, width = 1440.dp))
        assertFalse(barsFadeContent(television = false, desktopChrome = true, touch = false, width = 393.dp))
        assertFalse(barsFadeContent(television = true, desktopChrome = false, touch = false, width = 1920.dp))
    }

    @Composable
    private fun page(modifier: Modifier) {
        // A grid scrolls by row: index 6 is the third row, so there is something above it.
        FocusLazyVerticalGrid(GridCells.Fixed(3), modifier,
            state = rememberLazyGridState(initialFirstVisibleItemIndex = 6)) {
            items(90) { Box(Modifier.fillMaxWidth().height(180.dp).background(Palette.raised)) }
        }
    }

    private fun phone(barsFade: Boolean, bottomBar: Boolean, content: @Composable BoxScope.() -> Unit) =
        ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                CompositionLocalProvider(LocalBarsFadeContent provides barsFade, LocalBottomBarFades provides bottomBar) {
                    Box(Modifier.fillMaxSize(), content = content)
                }
            } }
        }

    private fun readColumn(scene: ImageComposeScene, x: Int, fromY: Int, rows: Int): List<Int> =
        scene.render(frame++ * 16_666_667L).use { image ->
            Bitmap.makeFromImage(image).use { pixels -> (0 until rows).map { row -> pixels.getColor(x, fromY + row) } }
        }

    private fun scrollToEnd(scene: ImageComposeScene) {
        var before = -1f
        repeat(60) {
            val scroll = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                .maxByOrNull { it.boundsInRoot.height } ?: fail("no scrolling page")
            val value = scroll.config[SemanticsProperties.VerticalScrollAxisRange].value()
            if (value <= before + .5f) return
            before = value
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
            frames(scene, 6)
        }
        fail("the page never reached its end")
    }

    private fun assertNoSteps(column: List<Int>, where: String) = column.zipWithNext().forEach { (a, b) ->
        assertTrue(distance(a, b) <= 4, "$where: a hard edge between ${hex(a)} and ${hex(b)}")
    }

    private fun assertNear(expected: Int, actual: Int, message: String) =
        assertTrue(distance(expected, actual) <= 3, "$message: expected ${hex(expected)}, was ${hex(actual)}")

    private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }
    private fun hex(color: Int) = "#" + color.toUInt().toString(16).padStart(8, '0')

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private companion object {
        const val WIDTH = 393
        const val HEIGHT = 852
    }
}
