package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.components.LocalPhoneScroll
import org.viptv.app.components.PhoneBrowseTitleGap
import org.viptv.app.components.PhoneHeader
import org.viptv.app.components.PhoneScroll
import org.viptv.app.connection.Profile
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.features.home.HomeScreen
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
class PhoneBrowseHeaderUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val profile = Profile("qa", "QA profile", null, avatarId = "initial-blue")

    @Test fun pillsSitAtTheSameSpotOnEveryScope() {
        val expected = tabRect(scene(Screen.Home, browse = false))
        for (screen in listOf(Screen.Movies, Screen.Series, Screen.Anime)) {
            val rect = tabRect(scene(screen, browse = false))
            assertEquals(expected.top, rect.top, 1f, "$screen pills' top")
            assertEquals(expected.bottom, rect.bottom, 1f, "$screen pills' bottom")
        }
    }

    @Test fun theTitleSitsOneGapBelowThePillsOnEveryBrowseScope() {
        for (screen in listOf(Screen.Movies, Screen.Series, Screen.Anime)) {
            val s = scene(screen, browse = true)
            try {
                frames(s)
                val tabs = tabs(s)
                val title = titleNode(s, screen.name)
                assertNotNull(title, "$screen title")
                val gap = title.boundsInRoot.top - tabs.maxOf { it.boundsInRoot.bottom }
                assertEquals(PhoneBrowseTitleGap.value, gap, 1f, "$screen title gap")
            } finally { s.close() }
        }
    }

    @Test fun scrollingTheGridCollapsesTheTitleAndKeepsThePills() {
        val s = scene(Screen.Anime, browse = true)
        try {
            frames(s)
            val pillsTop = tabs(s).minOf { it.boundsInRoot.top }
            val pillsBottom = tabs(s).maxOf { it.boundsInRoot.bottom }
            assertNotNull(titleNode(s, "Anime"), "the title starts visible")
            scrollDown(s)
            frames(s, 40)
            assertNull(titleNode(s, "Anime"), "the title collapses once the grid scrolls")
            assertEquals(pillsTop, tabs(s).minOf { it.boundsInRoot.top }, 1f, "pills keep their top")
            assertEquals(pillsBottom, tabs(s).maxOf { it.boundsInRoot.bottom }, 1f, "pills keep their bottom")
        } finally { s.close() }
    }

    @Test fun capturesHomeAndAnimeForThePr() {
        var s = scene(Screen.Home, browse = false)
        try { frames(s); capture(s, "home-top"); scrollDown(s); frames(s, 32); capture(s, "home-scrolled") } finally { s.close() }
        s = scene(Screen.Anime, browse = true)
        try { frames(s); capture(s, "anime-top"); scrollDown(s); frames(s, 40); capture(s, "anime-scrolled") } finally { s.close() }
    }

    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_BROWSE_HEADER_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }

    private fun scene(screen: Screen, browse: Boolean): ImageComposeScene {
        frame = 0
        return ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme(false) {
                CompositionLocalProvider(LocalPhoneScroll provides PhoneScroll()) {
                    Surface(Modifier.fillMaxSize(), color = Palette.background) {
                        Column {
                            PhoneHeader(screen, profile) {}
                            if (browse) BrowseScreen(screen, null, false, {}, loadedCards = animeCards, controls = null, busy = false)
                            else HomeScreen(home(), false, {}, {}, busy = false)
                        }
                    }
                }
            }
        }
    }

    private fun home() = HomeSnapshot(Scope("local", "device", "one", 1), 1, rows = (1..4).map { row ->
        HomeRow(id = "row$row", title = "Trending $row", provider_id = "simkl", state = RowState.READY,
            cards = (1..30).map(::card))
    }, hero = card(0), complete = true)

    private fun card(i: Int) = Card(MediaIdentity("tt$i", "movie", emptyList()), provider_id = "simkl", title = "Title $i", badges = emptyList())
    private val animeCards = (1..40).map { i -> Card(MediaIdentity("anime$i", "anime", emptyList()), category = "anime", provider_id = "simkl", title = "Anime $i", badges = emptyList()) }

    private fun scrollDown(scene: ImageComposeScene) {
        val grid = nodes(scene).filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            .maxByOrNull { it.boundsInRoot.height } ?: fail("no scrolling page")
        grid.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 900f)
    }

    private fun tabRect(scene: ImageComposeScene): Rect {
        frames(scene)
        val bounds = tabs(scene).map { it.boundsInRoot }
        return Rect(
            left = bounds.minOf { it.left }, top = bounds.minOf { it.top },
            right = bounds.maxOf { it.right }, bottom = bounds.maxOf { it.bottom })
    }

    private fun tabs(scene: ImageComposeScene) = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
    private fun titleNode(scene: ImageComposeScene, label: String) = nodes(scene).firstOrNull {
        text(it) == label && it.config.getOrNull(SemanticsProperties.Role) != Role.Tab
    }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}