package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.BottomBarHeight
import org.viptv.app.components.BottomNavigation
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.bottomBarClearance
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.settings.AccountIdentityScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

// The window's safe area is applied once, around every page and the bar; a page reserves the bar's box and nothing else.
@OptIn(ExperimentalComposeUiApi::class)
class BottomBarSafeAreaUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Config(val name: String, val width: Dp, val height: Dp, val inset: Dp, val density: Float)

    private val configs = buildList {
        for ((orientation, width, height) in listOf(Triple("portrait", 393.dp, 852.dp), Triple("landscape", 600.dp, 340.dp)))
            for ((navigation, inset) in listOf("gesture" to 24.dp, "three-button" to 48.dp))
                for (density in listOf(1f, 2f))
                    add(Config("$orientation $navigation at ${density}x", width, height, inset, density))
    }

    private val cards = (1..24).map {
        Card(MediaIdentity("safe-area-$it", "movie", emptyList()), provider_id = "addon:1", title = "Fixture title $it", badges = emptyList())
    }

    @Test fun theBarSitsAboveTheSafeAreaOnceAndThePageReservesOnlyTheBar() {
        for (config in configs) for ((page, content) in pages()) assertBarClearsThePage(config, page, content)
    }

    private fun pages(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "browse" to { BrowseScreen(Screen.Movies, null, false, {}, cards) },
        "settings" to { AccountIdentityScreen(AccountIdentity("1", "viewer", "Viewer", "owner"), false, null, {}) },
    )

    private fun assertBarClearsThePage(config: Config, page: String, content: @Composable () -> Unit) {
        val barHeight = BottomBarHeight.value * config.density
        val expectedBarTop = (config.height.value - config.inset.value) * config.density - barHeight
        var barTop = 0f
        val barred = scene(config, content, bar = true, clearance = bottomBarClearance()) { barTop = it }
        try {
            frames(barred)
            assertTrue(abs(barTop - expectedBarTop) <= 1f,
                "${config.name} $page: the bar starts at $barTop, not above the ${config.inset} safe area at $expectedBarTop")
            capture(barred, "bottom-bar-${config.name.replace(' ', '-')}-$page")
            scrollToEnd(barred)
            capture(barred, "bottom-end-${config.name.replace(' ', '-')}-$page")
        } finally { barred.close() }

        val cleared = end(config, content, bottomBarClearance())
        assertTrue(cleared.lowest <= barTop + .5f,
            "${config.name} $page: the last content at ${cleared.lowest} sits behind the bar at $barTop")
        val bare = end(config, content, 0.dp)
        val bareGap = (config.height.value - config.inset.value) * config.density - bare.lowest
        val clearedGap = barTop - cleared.lowest
        assertTrue(clearedGap <= bareGap + 1f,
            "${config.name} $page: the clearance leaves a $clearedGap band above the bar where the page's own end leaves $bareGap")
        if (!bare.scrolled) return
        assertTrue(abs((bare.lowest - cleared.lowest) - barHeight) <= 1f,
            "${config.name} $page: the clearance moves the last content from ${bare.lowest} to ${cleared.lowest}, not by the bar's $barHeight")
    }

    private class End(val lowest: Float, val scrolled: Boolean)

    private fun end(config: Config, content: @Composable () -> Unit, clearance: Dp): End {
        frame = 0
        val scene = scene(config, content, bar = false, clearance = clearance) {}
        try {
            frames(scene)
            val scrolled = scrollToEnd(scene)
            frames(scene, 6)
            return End(lowestContent(scene, (config.height.value * config.density).toInt()), scrolled)
        } finally { scene.close() }
    }

    private fun scene(config: Config, content: @Composable () -> Unit, bar: Boolean, clearance: Dp,
        placeBar: (Float) -> Unit): ImageComposeScene {
        val width = (config.width.value * config.density).toInt()
        val height = (config.height.value * config.density).toInt()
        return ImageComposeScene(width, height, Density(config.density)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Box(Modifier.fillMaxSize().padding(bottom = config.inset)) {
                    CompositionLocalProvider(LocalBottomBarFades provides true, LocalBottomClearance provides clearance) {
                        Box(Modifier.fillMaxSize()) {
                            content()
                            if (bar) Box(Modifier.align(Alignment.BottomCenter).onGloballyPositioned { placeBar(it.boundsInRoot().top) }) {
                                BottomNavigation(Screen.Home, {})
                            }
                        }
                    }
                }
            } }
        }
    }

    private fun lowestContent(scene: ImageComposeScene, height: Int): Float {
        val background = Palette.background.toArgb()
        var lowest = -1
        scene.render(frame++ * 16_666_667L).use { image ->
            Bitmap.makeFromImage(image).use { pixels ->
                for (y in height - 1 downTo 0) {
                    for (x in 0 until image.width step 2) {
                        if (distance(pixels.getColor(x, y), background) > 2) { lowest = y; break }
                    }
                    if (lowest >= 0) break
                }
            }
        }
        if (lowest < 0) fail("the page draws nothing")
        return lowest.toFloat()
    }

    private fun scrollToEnd(scene: ImageComposeScene): Boolean {
        var before = -1f
        repeat(60) {
            val scroll = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
                .maxByOrNull { it.boundsInRoot.height } ?: fail("no vertical scroller")
            val value = scroll.config[SemanticsProperties.VerticalScrollAxisRange].value()
            if (value <= before + 0.5f) return before > 0f
            before = value
            scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 3000f)
            frames(scene, 4)
        }
        fail("the page never reached its end")
    }

    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_BOTTOM_BAR_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs()
            scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }

    private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 10) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
