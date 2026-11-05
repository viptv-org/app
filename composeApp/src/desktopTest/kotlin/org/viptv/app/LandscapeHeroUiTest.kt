package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.components.LocalPhoneScroll
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.LocalHeaderPills
import org.viptv.app.components.PhoneScroll
import org.viptv.app.components.TabletMinSide
import org.viptv.app.connection.Profile
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.TABLET_HERO_FRAME_TAG
import org.viptv.app.features.home.TabletHeroLandscapeHeight
import org.viptv.app.features.home.TabletHeroPortraitHeight
import org.viptv.app.features.home.TabletHeroShortMin
import org.viptv.app.features.home.tabletHeroFrame
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LandscapeHeroUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("qa", "QA profile", null, avatarId = "initial-blue")
    private val title = "Fixture film"

    @Test fun theHeroHeightFollowsTheViewportBelowATabletsShortSide() {
        for ((width, viewport) in listOf(411.dp to 390.dp, 740.dp to 360.dp, 914.dp to 411.dp, 844.dp to 300.dp, 640.dp to 240.dp)) {
            assertEquals(viewport, tabletHeroFrame(width, viewport).height, "$width wide in a $viewport tall viewport fills it")
            assertTrue(tabletHeroFrame(width, viewport).short, "$width wide in a $viewport tall viewport is short")
        }
        for (width in listOf(411.dp, 844.dp, 914.dp)) {
            assertEquals(TabletHeroShortMin, tabletHeroFrame(width, 100.dp).height, "$width wide never goes under the floor")
        }
        assertFalse(tabletHeroFrame(844.dp, TabletMinSide).short, "a tablet's short side is not short")
        assertEquals(TabletHeroPortraitHeight, tabletHeroFrame(844.dp, TabletMinSide).height, "844 wide keeps its portrait frame at the short side")
    }

    @Test fun thePortraitAndLandscapeTabletFramesStayAsTheyAre() {
        assertEquals(TabletHeroPortraitHeight, tabletHeroFrame(899.dp, 1280.dp).height, "a portrait tablet keeps 640")
        assertEquals(TabletHeroPortraitHeight, tabletHeroFrame(800.dp, null).height, "no viewport means the portrait frame")
        assertEquals(TabletHeroLandscapeHeight, tabletHeroFrame(900.dp, 800.dp).height, "a landscape tablet keeps 520")
        assertEquals(TabletHeroLandscapeHeight, tabletHeroFrame(1280.dp, 800.dp).height, "a wide landscape tablet keeps 520")
    }

    @Test fun theHeroCopyWithItsActionsSitsInViewOnEveryPhoneLandscape() {
        for ((width, height) in listOf(844 to 390, 914 to 411, 740 to 360, 640 to 360)) {
            val scene = shell(width, height)
            try {
                frames(scene, 40)
                for (label in listOf(title, "Play", "More info")) {
                    val node = nodes(scene).firstOrNull { text(it) == label || description(it) == label }
                    assertNotNull(node, "$width x $height: $label is drawn: ${nodes(scene).map { text(it).ifEmpty { description(it) } }}")
                    val bottom = node.positionInRoot.y + node.size.height
                    assertTrue(node.positionInRoot.y >= 0f && bottom <= height, "$width x $height: $label sits in the window, ends at $bottom")
                }
                assertHeroFits(scene, width, height, false)
            } finally { scene.close() }
        }
    }

    @Test fun theLoadingHeroTakesTheSameHeightAsTheLoadedOne() {
        for ((width, height) in listOf(844 to 390, 740 to 360)) {
            val scene = shell(width, height, busy = true)
            try { frames(scene, 40); assertHeroFits(scene, width, height, true) } finally { scene.close() }
        }
    }

    @Test fun capturesTheLandscapeAndPortraitHeroesForThePr() {
        var scene = shell(844, 390)
        try { frames(scene, 40); capture(scene, "landscape-844x390") } finally { scene.close() }
        scene = shell(914, 411)
        try { frames(scene, 40); capture(scene, "landscape-914x411") } finally { scene.close() }
        scene = shell(390, 844, tablet = false, withHeader = true)
        try { frames(scene, 40); capture(scene, "portrait-390x844") } finally { scene.close() }
        scene = shell(800, 1280, withHeader = false)
        try { frames(scene, 40); capture(scene, "tablet-800x1280") } finally { scene.close() }
    }

    private fun assertHeroFits(scene: ImageComposeScene, width: Int, height: Int, skeleton: Boolean) {
        val page = nodes(scene).first { tag(it) == "page" }
        val hero = nodes(scene).first { tag(it) == TABLET_HERO_FRAME_TAG }
        val pageBottom = page.positionInRoot.y + page.size.height
        val heroBottom = hero.positionInRoot.y + hero.size.height
        assertTrue(heroBottom <= pageBottom + 1f, "$width x $height: the hero${if (skeleton) "skeleton" else ""} ends inside the page, ends at $heroBottom of $pageBottom")
        val expected = tabletHeroFrame(Dp(page.size.width.toFloat()), Dp(page.size.height.toFloat())).height
        assertEquals(expected.value, hero.size.height.toFloat(), 1f, "$width x $height: the hero takes the page height it is given")
    }

    private fun shell(width: Int, height: Int, busy: Boolean = false, tablet: Boolean = true, withHeader: Boolean = true) = scene(width, height) {
        CompositionLocalProvider(LocalPhoneScroll provides PhoneScroll(), LocalHeaderPills provides withHeader) {
            Column(Modifier.fillMaxSize()) {
                if (withHeader) PhoneHeader(Screen.Home, profile) {}
                Box(Modifier.weight(1f).testTag("page")) {
                    HomeScreen(if (busy) null else home(), false, {}, {}, busy = busy, tablet = tablet)
                }
            }
        }
    }

    private fun home() = HomeSnapshot(Scope("local", "device", "one", 1), 1, rows = (1..3).map { row ->
        HomeRow(id = "row$row", title = "Trending $row", provider_id = "simkl", state = RowState.READY, cards = (1..12).map(::card))
    }, hero = hero, complete = true)

    private val hero = Card(MediaIdentity("tt1", "movie", emptyList()), provider_id = "simkl", title = "Fixture film",
        description = "Across the Fiore kingdom, wizards join guilds and make their pay by filling magical needs.", badges = emptyList())

    private fun card(i: Int) = Card(MediaIdentity("tt$i", "movie", emptyList()), provider_id = "simkl", title = "Title $i", badges = emptyList())

    private var frame = 0L
    private fun scene(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
    }
    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_HERO_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun description(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().firstOrNull().orEmpty()
    private fun tag(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
}