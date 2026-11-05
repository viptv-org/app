package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarScreen
import org.viptv.app.features.calendar.TvCalendarPreview
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.details.DesktopEpisodeRow
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.app.features.home.DesktopHero
import org.viptv.app.features.home.TabletHero
import org.viptv.app.features.home.TvBrowseSummary
import org.viptv.app.features.home.TvHeroSummary
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.TvLivePreview
import org.viptv.app.features.player.TvNextEpisode
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ProviderMarkupUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val markup = "First line<br>Second <i>italic</i> &amp; more"
    private val prose = "First line\nSecond italic & more"
    private val card = Card(MediaIdentity("fixture-episode", "episode", emptyList()), provider_id = "simkl",
        title = "Furry tale", description = markup, badges = emptyList())
    private val channel = LiveChannel(card, id = "one", connectionId = "m3u:1")
    private val program = LiveProgram("p1", "Morning news", 0, 180, description = markup)
    private val day = CalendarDay("one", "Thu", "15", "Today", "Oct 15")
    private val entry = CalendarEntry("release", "one", "4 PM", card, aired = true, description = markup)
    private val requester = FocusRequester()
    private val focus = FocusRequester()

    private data class Surface(val name: String, val width: Int, val height: Int, val television: Boolean,
        val content: @Composable () -> Unit)

    private fun surfaces(): List<Surface> = listOf(
        Surface("DetailEpisodeCard", 1200, 800, false) { DetailEpisodeCard(card, phone = false, play = {}) },
        Surface("DesktopEpisodeRow", 1200, 800, false) { DesktopEpisodeRow(card, play = {}) },
        Surface("TvEpisodeCard", 960, 720, true) { TvEpisodeCard(card, 1f, play = {}, onFocus = {}, requester = null, up = focus) },
        Surface("TvHeroSummary", 1920, 1080, true) { TvHeroSummary(card, 1f) },
        Surface("TvBrowseSummary", 1920, 1080, true) { TvBrowseSummary(card, 1f) },
        Surface("DesktopHero", 1280, 800, false) { DesktopHero(card, open = {}, play = {}) },
        Surface("TabletHero", 1024, 768, false) { TabletHero(card, open = {}, play = {}, navigate = {}, save = {}, saved = false) },
        Surface("LivePreview", 1200, 800, false) {
            LivePreview(channel, program, 30, phone = false, television = false, scale = 1f, watch = {}, favourite = null)
        },
        Surface("TvLivePreview", 1920, 1080, true) { TvLivePreview(channel, program, 30, 1f, favorite = null, watch = {}) },
        Surface("TvCalendarPreview", 1920, 1080, true) {
            TvCalendarPreview(entry, action = null, scale = 1f, requester = requester, left = null, up = focus, open = {}, watch = {})
        },
        Surface("CalendarScreen", 1920, 1080, true) {
            CalendarScreen(listOf(day), listOf(entry), television = true, open = {}, manage = {})
        },
        Surface("TvNextEpisode", 1920, 1080, true) { TvNextEpisode(current = null, next = card, scale = 1f, play = {}, keepWatching = {}) },
    )

    @Test fun everySurfaceDrawsProviderDescriptionsAsProse() {
        for (surface in surfaces()) {
            val scene = ImageComposeScene(surface.width, surface.height, Density(1f)) {
                ViptvTheme(surface.television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { surface.content() } }
            }
            try {
                val drawn = lines(scene)
                assertTrue(drawn.contains(prose), "${surface.name} renders the description as prose: $drawn")
                val raw = drawn.filter { it.contains("<br>") || it.contains("<i>") || it.contains("&amp;") }
                assertEquals(emptyList(), raw, "${surface.name} leaves no provider markup on screen")
            } finally { scene.close() }
        }
    }

    private fun lines(scene: ImageComposeScene): List<String> {
        repeat(8) { scene.render(it * 16_666_667L).close() }
        return scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
    }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}