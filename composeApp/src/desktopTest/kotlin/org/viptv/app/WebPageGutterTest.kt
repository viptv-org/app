package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import org.viptv.app.components.TopNavigation
import org.viptv.app.connection.BrowseQuery
import org.viptv.app.connection.DiscoveryCatalog
import org.viptv.app.connection.DiscoveryControls
import org.viptv.app.connection.FeatureAvailability
import org.viptv.app.features.browse.BrowserBrowseScreen
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WebPageGutterTest {
    @get:Rule val uiThread = UiThreadRule()

    private val genre = QueryControl("genre", "Genre", ControlKind.SINGLECHOICE, false,
        listOf("Action", "Drama").map { Choice(it.lowercase(), it) })
    private val catalog = DiscoveryCatalog("top", "Movies", "movie", "c1", controls = listOf(genre), connection_name = "Cinemeta")
    private val controls = DiscoveryControls("stremio", listOf("movie"), listOf(genre), catalogs = listOf(catalog),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))

    private val cards = (1..12).map {
        Card(MediaIdentity("gutter-$it", "movie", emptyList()), provider_id = "addon:1", title = "Gutter title $it", badges = emptyList())
    }
    private val days = listOf(CalendarDay("today", "Mon", "1", "Today", "October 1"))
    private val entry = CalendarEntry("cal-1", "today", "4 PM",
        Card(MediaIdentity("cal", "series", emptyList()), provider_id = "addon:1", title = "Gutter entry", badges = emptyList()))

    private companion object {
        const val TopNavBrandTag = "top-nav-brand"
    }

    @Test fun webPagesSitOnTheNavBrandGutter() {
        for (width in listOf(1920, 900)) {
            val gutter = if (width < 1000) 16f else 57f
            for (screen in listOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime)) {
                val scene = browserScene(width, screen)
                try { assertGutter(scene, screen.name, "Gutter title 1", gutter) } finally { scene.close() }
            }
            val calendar = calendarScene(width)
            try { assertGutter(calendar, "Calendar", "Gutter entry", gutter) } finally { calendar.close() }
        }
    }

    private fun browserScene(width: Int, screen: Screen) = scene(width, 1080) {
        TopNavigation(screen, false, {}, profile = null)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BrowserBrowseScreen(screen, cards, controls, BrowseQuery("movie", "top", "c1"), false, null, null,
                {}, { _, _ -> }, { _, _ -> }, {})
        }
    }

    private fun calendarScene(width: Int) = scene(width, 1080) {
        TopNavigation(Screen.Calendar, false, {}, profile = null)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            CalendarScreen(days, listOf(entry), false, {}, {}, controls = true, browser = true)
        }
    }

    private fun scene(width: Int, height: Int, content: @Composable ColumnScope.() -> Unit): ImageComposeScene {
        frame = 0
        return ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(false, pointer = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Column(Modifier.fillMaxSize()) { content() }
            } }
        }
    }

    private fun assertGutter(scene: ImageComposeScene, title: String, firstCard: String, expected: Float) {
        frames(scene)
        val nodes = nodes(scene)
        val brand = nodes.first { it.config.getOrNull(SemanticsProperties.TestTag) == TopNavBrandTag }
        assertNear(expected, brand.boundsInRoot.left, "$title: the nav brand rests on the gutter")
        val pageTitle = nodes.firstOrNull {
            text(it) == title && !it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.top >= 68f
        } ?: fail("$title: no page title below the nav; texts=" + nodes.map(::text).filter(String::isNotBlank))
        assertNear(brand.boundsInRoot.left, pageTitle.boundsInRoot.left, "$title: the page title lines up with the nav brand")
        val firstChip = nodes.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            .minByOrNull { it.boundsInRoot.left } ?: fail("$title: no filter chip")
        assertNear(brand.boundsInRoot.left, firstChip.boundsInRoot.left, "$title: the first chip lines up with the nav brand")
        val firstCardNode = nodes.firstOrNull { it.config.contains(SemanticsActions.OnClick) && firstCard in texts(it) }
            ?: fail("$title: no $firstCard")
        assertNear(brand.boundsInRoot.left, firstCardNode.boundsInRoot.left, "$title: the first card lines up with the nav brand")
    }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) <= 1f, "$message: expected $expected, was $actual")

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
}