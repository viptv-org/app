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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.viptv.app.components.FocusLazyVerticalGrid
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.LocalPhoneScroll
import org.viptv.app.components.PhoneBottomBar
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.PhoneScroll
import org.viptv.app.connection.Profile
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhoneBottomBarTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("qa", "QA profile", null, avatarId = "initial-blue")

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
        val scene = animePage(scroll, colored = true)
        try {
            frames(scene)
            assertFalse(scroll.barHidden, "the bar starts visible at the top")
            scrollTo(scene, 900f); frames(scene, 24)
            assertTrue(scroll.barHidden, "scrolling down hides the bar")
            scrollTo(scene, -48f); frames(scene, 24)
            assertFalse(scroll.barHidden, "scrolling up 48 dp shows the bar again")
        } finally { scene.close() }
    }

    @Test fun noOpaqueBandCoversTheBarRegionWhileHidden() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        val scene = animePage(scroll, colored = true)
        try {
            frames(scene)
            scrollTo(scene, 4000f); frames(scene, 40)
            assertTrue(scroll.barHidden, "the bar hides after scrolling down")
            val colour = pixel(scene, 195, 800)
            assertTrue(isLight(colour), "content reaches the bar region once hidden: #${colour.toUInt().toString(16)}")
        } finally { scene.close() }
    }

    @Test fun capturesTheBarForThePr() {
        val scroll = PhoneScroll()
        scroll.revealAtPx(48f)
        val scene = animePage(scroll, colored = false)
        try {
            frames(scene); capture(scene, "bar-visible-top")
            scrollTo(scene, 900f); frames(scene, 24); capture(scene, "bar-hidden")
            scrollTo(scene, -48f); frames(scene, 24); capture(scene, "bar-visible-again")
        } finally { scene.close() }
    }

    private fun animePage(scroll: PhoneScroll, colored: Boolean): ImageComposeScene {
        frame = 0
        return ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                val clearance = if (scroll.barHidden) 0.dp else 80.dp
                CompositionLocalProvider(LocalPhoneScroll provides scroll,
                    LocalBottomBarFades provides true, LocalBottomClearance provides clearance) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            if (colored) {
                                FocusLazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize()) {
                                    items(90) { Box(Modifier.fillMaxWidth().height(160.dp).background(Palette.text)) }
                                }
                            } else {
                                PhoneHeader(Screen.Anime, profile) {}
                                BrowseScreen(Screen.Anime, null, false, {}, loadedCards = animeCards, controls = null, busy = false)
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            PhoneBottomBar(Screen.Home, {}, hidden = scroll.barHidden)
                        }
                    }
                }
            }
        }
    }

    private val animeCards = (1..40).map { i ->
        Card(MediaIdentity("anime$i", "anime", emptyList()), category = "anime", provider_id = "simkl", title = "Anime $i", badges = emptyList())
    }

    private fun scrollTo(scene: ImageComposeScene, delta: Float) {
        gridNode(scene).config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta)
    }

    private fun gridNode(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        .maxByOrNull { it.boundsInRoot.height } ?: fail("no scrolling page")

    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_BOTTOM_BAR_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }

    private fun pixel(scene: ImageComposeScene, x: Int, y: Int): Int =
        scene.render(frame++ * 16_666_667L).use { image -> Bitmap.makeFromImage(image).use { it.getColor(x, y) } }

    private fun isLight(color: Int) = ((color shr 16) and 0xFF) > 200 && ((color shr 8) and 0xFF) > 200 && (color and 0xFF) > 200

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}