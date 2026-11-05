package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.viptv.app.components.BottomBarHeight
import org.viptv.app.components.LocalBottomBarFades
import org.viptv.app.components.LocalBottomClearance
import org.viptv.app.components.bottomBarClearance
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen

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

    private fun problems(page: Page, scale: Float): List<String> {
        val scene = ImageComposeScene(page.width, page.height, Density(1f, scale)) {
            CompositionLocalProvider(LocalBottomClearance provides if (page.bottomBar) bottomBarClearance() else 0.dp,
                LocalBottomBarFades provides page.bottomBar) {
                UiPreview(television = page.television, desktop = page.desktop, screen = page.screen, tvDetailVariant = page.variant)
            }
        }
        return try {
            settle(scene)
            val texts = textNodes(scene)
            val where = "${page.width}x${page.height}"
            val clipped = texts.mapNotNull { (label, node) ->
                val layout = layout(node) ?: return@mapNotNull null
                val paragraph = layout.multiParagraph
                val tooTall = paragraph.height > layout.size.height + 1f
                if (!tooTall) null
                else "$where: '$label' needs ${paragraph.height.toInt()}px and gets ${layout.size.height.toInt()}px"
            }
            val barTop = page.height - BottomBarHeight.value
            val overlaps = texts.flatMapIndexed { index, (label, node) ->
                texts.drop(index + 1).mapNotNull { (other, otherNode) ->
                    val hit = overlap(node.boundsInRoot, otherNode.boundsInRoot)
                    // The phone bar floats over the page: scrolled content under it is hidden, not overlapping.
                    if (hit <= 0f || page.bottomBar && (node.boundsInRoot.bottom > barTop || otherNode.boundsInRoot.bottom > barTop)) null
                    else "$where: '$label' overlaps '$other' by ${hit.toInt()}px²"
                }
            }
            clipped + overlaps
        } finally { scene.close() }
    }

    private fun overlap(a: Rect, b: Rect): Float {
        if (a.isEmpty || b.isEmpty) return 0f
        return max(0f, min(a.right, b.right) - max(a.left, b.left)) * max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
    }

    private fun settle(scene: ImageComposeScene) {
        var previous = ""
        scene.renderUntil(timeout = 10.seconds, what = "the page to settle") {
            val signature = textNodes(scene).joinToString("|") { (label, node) -> "$label@${node.boundsInRoot}" }
            val settled = signature.isNotEmpty() && signature == previous
            previous = signature
            settled
        }
    }

    private fun textNodes(scene: ImageComposeScene): List<Pair<String, SemanticsNode>> = scene.semanticsOwners
        .flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        .filter { it.config.contains(SemanticsActions.GetTextLayoutResult) }
        .mapNotNull { node -> label(node).takeIf { it.isNotBlank() }?.let { it to node } }

    private fun layout(node: SemanticsNode): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.singleOrNull()
    }

    private fun label(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

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

    private fun pages(): List<Page> = tvPages() + phonePages() + desktopPages()
}