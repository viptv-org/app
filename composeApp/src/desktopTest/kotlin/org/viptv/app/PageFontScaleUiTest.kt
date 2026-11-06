package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.viptv.app.components.ListEndFooter
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.OverlaysContentKey
import org.viptv.app.components.bottomBarClearance
import org.viptv.app.features.calendar.CalendarEntry
import org.viptv.app.features.calendar.CalendarRow
import org.viptv.app.features.details.DetailEpisodeCard
import org.viptv.app.features.details.DetailSeason
import org.viptv.app.features.details.DetailSeasonPicker
import org.viptv.app.features.details.EpisodeWatchedPill
import org.viptv.app.features.details.FavoriteAction
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveChannelRow
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity

/**
 * Every page, on the size its own platform draws it at, at 1.0x, 1.3x and 2.0x system font scale:
 * no text may be taller or wider than the box it was laid out in, and no two pieces of text may
 * overlap. Heights derived from the TV layout scale are not derived from the font scale, so a row
 * that owns a height clips its text and then collides with its neighbour.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PageFontScaleUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private data class Page(val name: String, val screen: Screen, val variant: String,
        val width: Int, val height: Int, val television: Boolean = false, val desktop: Boolean = false,
        val bottomBar: Boolean = false)

    private val scales = listOf(1f, 1.3f, 2f)

    private companion object {
        const val SCROLL_STEPS = 12
        const val SCROLL_FRACTION = 0.85f
    }

    @Test fun noPageClipsOrOverlapsItsTextAtAnyFontScale() {
        val filter = System.getenv("VIPTV_FONT_SCALE_PAGES")?.split(",")?.filter { it.isNotBlank() }?.toSet()
        val failures = mutableMapOf<String, List<String>>()
        for (page in pages().filter { filter == null || it.name in filter }) for (scale in scales) {
            problems(page, scale).takeIf { it.isNotEmpty() }?.let { failures["${page.name} @${scale}x"] = it }
        }
        assertTrue(failures.isEmpty(), failures.entries.joinToString("\n") { (where, found) ->
            "$where: ${found.size} problems: " + found.take(3).joinToString("; ")
        })
    }

    // A page is checked at the top and then screenful by screenful to its end: text below the fold is only composed once it scrolls in.
    private fun problems(page: Page, scale: Float): List<String> {
        val scene = ImageComposeScene(page.width, page.height, Density(1f, scale)) {
            CompositionLocalProvider(LocalBottomClearance provides if (page.bottomBar) bottomBarClearance() else 0.dp,
                LocalBottomBarFades provides page.bottomBar) {
                UiPreview(television = page.television, desktop = page.desktop, screen = page.screen, tvDetailVariant = page.variant)
            }
        }
        return try {
            settle(scene)
            val found = LinkedHashSet<String>()
            var scrolled = -1f
            repeat(SCROLL_STEPS) {
                found += check(scene, page.width, page.height)
                val at = scrollOffset(scene)
                if (at <= scrolled) return@repeat
                scrolled = at
                scrollByScreenful(scene)
                settle(scene)
            }
            found.toList()
        } finally { scene.close() }
    }

    private fun check(scene: ImageComposeScene, width: Int, height: Int): List<String> {
        val texts = textNodes(scene)
        val where = "${width}x${height}"
        val clipped = texts.mapNotNull { text ->
            val layout = layout(text.node) ?: return@mapNotNull null
            val paragraph = layout.multiParagraph
            val tooTall = paragraph.height > layout.size.height + 1f
            if (!tooTall) null
            else "$where: '${text.label}' needs ${paragraph.height.toInt()}px and gets ${layout.size.height.toInt()}px"
        }
        // A line break inside a word means the column is narrower than its text at this scale.
        val broken = texts.mapNotNull { text ->
            val layout = layout(text.node) ?: return@mapNotNull null
            val content = layout.layoutInput.text.text
            (1 until layout.lineCount).firstOrNull { line ->
                val split = layout.getLineEnd(line - 1)
                split in 1 until content.length && content[split - 1].isLetterOrDigit() && content[split].isLetterOrDigit()
            }?.let { line -> "$where: '${text.label}' breaks mid-word at line $line" }
        }
        val visible = texts.filter { visible(it.node) && !it.overlaid }
        val overlaps = visible.flatMapIndexed { index, text ->
            visible.drop(index + 1).mapNotNull { other ->
                val hit = overlap(text.node.boundsInRoot, other.node.boundsInRoot)
                if (hit <= 0f) null
                else "$where: '${text.label}' ${text.node.boundsInRoot} overlaps '${other.label}' ${other.node.boundsInRoot} by ${hit.toInt()}px²"
            }
        }
        return clipped + broken + overlaps
    }

    private fun scroller(scene: ImageComposeScene): SemanticsNode? = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        .filter { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        .maxByOrNull { it.boundsInRoot.height }

    private fun scrollOffset(scene: ImageComposeScene): Float =
        scroller(scene)?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value() ?: 0f

    private fun scrollByScreenful(scene: ImageComposeScene) {
        val node = scroller(scene) ?: return
        val step = (node.boundsInRoot.height * SCROLL_FRACTION).coerceAtLeast(200f)
        node.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, step)
    }

    // Partly scrolled out text is clipped by its scroller, so its box says nothing about what it covers.
    private fun visible(node: SemanticsNode): Boolean {
        val layout = layout(node) ?: return false
        val bounds = node.boundsInRoot
        return abs(bounds.width - layout.size.width) <= 1f && abs(bounds.height - layout.size.height) <= 1f
    }

    private fun overlap(a: Rect, b: Rect): Float {
        if (a.isEmpty || b.isEmpty) return 0f
        return max(0f, min(a.right, b.right) - max(a.left, b.left)) * max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    }

    private fun settle(scene: ImageComposeScene) {
        var previous = ""
        scene.renderUntil(timeout = 10.seconds, what = "the page to settle") {
            val signature = textNodes(scene).joinToString("|") { text -> "${text.label}@${text.node.boundsInRoot}" }
            val settled = signature.isNotEmpty() && signature == previous
            previous = signature
            settled
        }
    }

    private class TextNode(val label: String, val node: SemanticsNode, val overlaid: Boolean)

    private fun textNodes(scene: ImageComposeScene): List<TextNode> = scene.semanticsOwners.flatMap { owner ->
        fun walk(node: SemanticsNode, overlaid: Boolean): List<TextNode> {
            val covered = overlaid || node.config.contains(OverlaysContentKey)
            val own = if (node.config.contains(SemanticsActions.GetTextLayoutResult)) {
                label(node).takeIf { it.isNotBlank() }?.let { listOf(TextNode(it, node, covered)) }.orEmpty()
            } else emptyList()
            return own + node.children.flatMap { walk(it, covered) }
        }
        walk(owner.rootSemanticsNode, overlaid = false)
    }

    private fun layout(node: SemanticsNode): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.singleOrNull()
    }

    private fun label(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    // The components #866 names, driven directly so a page that hides them below the fold or behind a modal still gets checked.
    @Test fun everyPhoneComponentClipsNothingAtLargerFontScales() {
        val failures = mutableMapOf<String, List<String>>()
        for (component in components()) for (scale in scales) {
            componentProblems(component, scale).takeIf { it.isNotEmpty() }?.let { failures["${component.name} @${scale}x"] = it }
        }
        assertTrue(failures.isEmpty(), failures.entries.joinToString("\n") { (where, found) ->
            "$where: ${found.size} problems: " + found.take(3).joinToString("; ")
        })
    }

    private data class Component(val name: String, val width: Int, val height: Int, val content: @Composable () -> Unit)

    private fun components(): List<Component> = listOf(
        Component("save action", 390, 400) {
            Column(Modifier.padding(16.dp)) { FavoriteAction(detailCard(), saved = false, save = {}) }
        },
        Component("calendar release row", 390, 400) {
            Column(Modifier.padding(16.dp)) {
                CalendarRow(calendarEntry(), phone = true, television = false, scale = 1f, focus = {}, open = {}, watch = {})
            }
        },
        Component("live channel row", 390, 400) {
            Column(Modifier.padding(16.dp)) {
                LiveChannelRow(liveChannel(), now = 30, watch = {}, favourite = null)
            }
        },
        Component("list end", 390, 300) {
            Column(Modifier.padding(16.dp)) { ListEndFooter(ended = true) }
        },
        Component("season picker", 390, 300) {
            Column(Modifier.padding(16.dp)) {
                val season = DetailSeason(1, listOf(episodeCard()))
                DetailSeasonPicker(listOf(season), season, phone = true, choose = {})
            }
        },
        Component("episode card", 390, 400) {
            Column(Modifier.padding(16.dp)) { DetailEpisodeCard(episodeCard(), phone = true, play = {}) }
        },
        Component("watched pill", 390, 300) {
            Column(Modifier.padding(16.dp)) { EpisodeWatchedPill(television = false) }
        },
    )

    private fun componentProblems(component: Component, scale: Float): List<String> {
        val scene = ImageComposeScene(component.width, component.height, Density(1f, scale)) {
            ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Palette.background) { component.content() } }
        }
        return try {
            settle(scene)
            check(scene, component.width, component.height)
        } finally { scene.close() }
    }

    private fun detailCard() = Card(MediaIdentity("tt1000001", "movie", emptyList()), provider_id = "addon:1",
        title = "A title with a long enough name to take room", badges = listOf("4K", "HDR"))

    private fun calendarEntry(): CalendarEntry {
        val parent = Card(MediaIdentity("simkl:tv:21", "series", emptyList()), provider_id = "simkl",
            title = "Scheduled series", badges = emptyList())
        return CalendarEntry("one", "one", "12:00 PM", parent, subtitle = "S2 E3 · [Episode title] · Prime Video", aired = true)
    }

    private fun liveChannel() = LiveChannel(Card(MediaIdentity("channel-one", "live", emptyList()), provider_id = "m3u:1",
        title = "Channel One", badges = listOf("LIVE")), id = "one", connectionId = "m3u:1",
        programs = listOf(LiveProgram("now", "[Football Pre-game]", 0, 30, description = "[Local and national headlines, weather and sports.]"),
            LiveProgram("next", "[Quiz Night]", 30, 60)))

    private fun episodeCard(): Card {
        val show = Card(MediaIdentity("tt1000002", "series", emptyList()), provider_id = "addon:1", title = "Fixture series",
            badges = emptyList())
        return show.copy(identity = MediaIdentity("tt1000002:1:1", "series", emptyList(), EpisodeCoordinates("aired", 1, 1)),
            parent_identity = show.identity, title = "Episode One", subtitle = "52m · Watched", progress = 1.0)
    }

    private fun tvPages() = listOf(
        Page("tv-home", Screen.Home, "Home", 1920, 1080, television = true),
        Page("tv-home-loading", Screen.Home, "Loading", 1920, 1080, television = true),
        Page("tv-movies", Screen.Movies, "Movies", 1920, 1080, television = true),
        Page("tv-series", Screen.Series, "Series", 1920, 1080, television = true),
        Page("tv-anime", Screen.Anime, "Anime", 1920, 1080, television = true),
        Page("tv-browse-empty", Screen.Movies, "Empty", 1920, 1080, television = true),
        Page("tv-search", Screen.Search, "Search", 1920, 1080, television = true),
        Page("tv-library", Screen.Library, "Library", 1920, 1080, television = true),
        Page("tv-library-empty", Screen.Library, "Empty", 1920, 1080, television = true),
        Page("tv-history", Screen.History, "History", 1920, 1080, television = true),
        Page("tv-calendar", Screen.Calendar, "Calendar", 1920, 1080, television = true),
        Page("tv-live", Screen.Live, "Live", 1920, 1080, television = true),
        Page("tv-title-movie", Screen.Details, "Movie", 1920, 1080, television = true),
        Page("tv-title-series", Screen.Details, "Series", 1920, 1080, television = true),
        Page("tv-seasons", Screen.Details, "SeriesSeasons", 1920, 1080, television = true),
        Page("tv-sources", Screen.Sources, "Sources", 1920, 1080, television = true),
        Page("tv-sources-empty", Screen.Sources, "Empty", 1920, 1080, television = true),
        Page("tv-settings-playback", Screen.Settings, "Playback", 1920, 1080, television = true),
        Page("tv-settings-subs", Screen.Settings, "Subs", 1920, 1080, television = true),
        Page("tv-settings-addons", Screen.Settings, "Addons", 1920, 1080, television = true),
        Page("tv-settings-sources", Screen.Settings, "Sources", 1920, 1080, television = true),
        Page("tv-settings-parental", Screen.Settings, "Parental", 1920, 1080, television = true),
        Page("tv-settings-account", Screen.Settings, "Account", 1920, 1080, television = true),
        Page("tv-settings-display", Screen.Settings, "Display", 1920, 1080, television = true),
        Page("tv-settings-about", Screen.Settings, "About", 1920, 1080, television = true),
        Page("tv-settings-profile", Screen.Settings, "Profile", 1920, 1080, television = true),
        Page("tv-profiles", Screen.Profiles, "Profiles", 1920, 1080, television = true),
        Page("tv-profiles-locked", Screen.Profiles, "Locked", 1920, 1080, television = true),
        Page("tv-pin", Screen.ProfilePin, "Pin", 1920, 1080, television = true),
        Page("tv-profile-edit", Screen.ProfileEdit, "ProfileEdit", 1920, 1080, television = true),
        Page("tv-player", Screen.Player, "Player", 1920, 1080, television = true),
        Page("tv-signin", Screen.Connect, "Welcome", 1920, 1080, television = true),
        Page("tv-signed-in", Screen.Connect, "SignedIn", 1920, 1080, television = true),
        Page("tv-splash", Screen.Connect, "Splash", 1920, 1080, television = true),
        Page("tv-pairing", Screen.Connect, "Pairing", 1920, 1080, television = true),
    )

    private fun phonePages() = listOf(
        Page("phone-home", Screen.Home, "Home", 390, 844, bottomBar = true),
        Page("phone-movies", Screen.Movies, "Movies", 390, 844, bottomBar = true),
        Page("phone-series", Screen.Series, "Series", 390, 844, bottomBar = true),
        Page("phone-search", Screen.Search, "Search", 390, 844, bottomBar = true),
        Page("phone-library", Screen.Library, "Library", 390, 844, bottomBar = true),
        Page("phone-library-long", Screen.Library, "Long", 390, 844, bottomBar = true),
        Page("phone-history", Screen.History, "History", 390, 844, bottomBar = true),
        Page("phone-calendar", Screen.Calendar, "Calendar", 390, 844, bottomBar = true),
        Page("phone-live", Screen.Live, "Live", 390, 844, bottomBar = true),
        Page("phone-title-movie", Screen.Details, "Movie", 390, 844, bottomBar = true),
        Page("phone-title-series", Screen.Details, "Series", 390, 844, bottomBar = true),
        Page("phone-sources", Screen.Sources, "Sources", 390, 844, bottomBar = true),
        Page("phone-settings", Screen.Settings, "Playback", 390, 844, bottomBar = true),
        Page("phone-settings-addons", Screen.Settings, "Addons", 390, 844, bottomBar = true),
        Page("phone-profiles", Screen.Profiles, "Profiles", 390, 844, bottomBar = true),
        Page("phone-player", Screen.Player, "Player", 390, 844, bottomBar = true),
    )

    private fun desktopPages() = listOf(
        Page("desktop-home", Screen.Home, "Home", 1280, 800, desktop = true),
        Page("desktop-movies", Screen.Movies, "Movies", 1280, 800, desktop = true),
        Page("desktop-search", Screen.Search, "Search", 1280, 800, desktop = true),
        Page("desktop-library", Screen.Library, "Library", 1280, 800, desktop = true),
        Page("desktop-history", Screen.History, "History", 1280, 800, desktop = true),
        Page("desktop-calendar", Screen.Calendar, "Calendar", 1280, 800, desktop = true),
        Page("desktop-live", Screen.Live, "Live", 1280, 800, desktop = true),
        Page("desktop-title-movie", Screen.Details, "Movie", 1280, 800, desktop = true),
        Page("desktop-title-series", Screen.Details, "Series", 1280, 800, desktop = true),
        Page("desktop-sources", Screen.Sources, "Sources", 1280, 800, desktop = true),
        Page("desktop-settings", Screen.Settings, "Playback", 1280, 800, desktop = true),
        Page("desktop-settings-addons", Screen.Settings, "Addons", 1280, 800, desktop = true),
        Page("desktop-profiles", Screen.Profiles, "Profiles", 1280, 800, desktop = true),
    )

    // A tablet is the touch layout that is neither phone nor desktop chrome: UiPreview draws it from the width alone.
    private fun tabletPages(): List<Page> = listOf(
        Triple("home", Screen.Home, "Home"),
        Triple("movies", Screen.Movies, "Movies"),
        Triple("search", Screen.Search, "Search"),
        Triple("library", Screen.Library, "Library"),
        Triple("library-long", Screen.Library, "Long"),
        Triple("history", Screen.History, "History"),
        Triple("calendar", Screen.Calendar, "Calendar"),
        Triple("live", Screen.Live, "Live"),
        Triple("title-movie", Screen.Details, "Movie"),
        Triple("title-series", Screen.Details, "Series"),
        Triple("sources", Screen.Sources, "Sources"),
        Triple("settings", Screen.Settings, "Playback"),
        Triple("settings-addons", Screen.Settings, "Addons"),
        Triple("profiles", Screen.Profiles, "Profiles"),
    ).flatMap { (name, screen, variant) ->
        listOf(1024 to 768, 1280 to 800).map { (width, height) ->
            Page("tablet-$name-${width}w", screen, variant, width, height)
        }
    }

    private fun pages(): List<Page> = tvPages() + phonePages() + desktopPages() + tabletPages()
}