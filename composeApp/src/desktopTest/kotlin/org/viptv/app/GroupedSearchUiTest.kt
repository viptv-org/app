package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class GroupedSearchUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun suppliedCarouselTitleRemainsVisibleForOneRowAndLaterProviderResultsKeepTheirOwnActions() {
        val first = Card(MediaIdentity("same-id", "movie", emptyList()), provider_id = "stremio", title = "First copy", badges = emptyList())
        val second = first.copy(title = "Second copy")
        val firstRow = SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = listOf(first))
        val rows = mutableStateOf(listOf(firstRow))
        var opened: Card? = null
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("same", emptyList(), emptyList(), false, null, true, {}, { opened = it },
                        editQuery = {}, searchRows = rows.value)
                }
            }
        }
        try {
            frames(scene, 0)
            assertTrue(nodes(scene).any { text(it).contains("First provider") }, "A single supplied row must keep its carousel title")
            rows.value = listOf(firstRow,
                SearchResultRow("addon:2", "Second provider", "stremio", addon_id = 2, cards = listOf(second)),
                SearchResultRow("addon:3", "Unavailable provider", "stremio", addon_id = 3, state = "Failed", message = "private upstream sentinel"))
            frames(scene, 20)
            val nodes = nodes(scene)
            assertTrue(nodes.any { text(it).contains("First copy") })
            assertTrue(nodes.any { text(it).contains("Second copy") })
            assertTrue(nodes.any { text(it).contains("This search provider could not respond") })
            assertFalse(nodes.any { text(it).contains("private upstream sentinel") })
            val choice = nodes.single { it.config.contains(SemanticsActions.OnClick) && text(it).contains("Second copy") }
            assertTrue(choice.config[SemanticsActions.OnClick].action!!.invoke())
            assertSame(second, opened)
        } finally { scene.close() }
    }

    @Test fun aFilterThatExcludesEveryProviderShowsOneEmptyState() {
        val movie = Card(MediaIdentity("only-title", "movie", emptyList()), provider_id = "stremio", title = "Only copy", badges = emptyList())
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("spider", emptyList(), emptyList(), false, null, true, {}, {},
                        editQuery = {}, filters = setOf("Channels"),
                        searchRows = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, cards = listOf(movie))))
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it.contains("No matches") })
            assertFalse(texts.any { it == "First provider" })
            assertFalse(texts.any { it == "Only copy" })
        } finally { scene.close() }
    }

    @Test fun anEmptySearchShowsTheDesignEmptyStateCopy() {
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("spider", emptyList(), emptyList(), false, null, true, {}, {}, editQuery = {})
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it.contains("No matches") })
        } finally { scene.close() }
    }

    @Test fun failedProviderBlocksSitBelowTheProvidersThatReturnedResults() {
        val result = Card(MediaIdentity("result-id", "movie", emptyList()), provider_id = "addon:1", title = "Returned title", badges = emptyList())
        val rows = listOf(
            SearchResultRow("simkl:anime", "Anime", "simkl", state = "Failed"),
            SearchResultRow("simkl:movie", "Movies", "simkl", state = "Failed"),
            SearchResultRow("addon:1", "Cinemeta", "stremio", addon_id = 1, cards = listOf(result)),
            SearchResultRow("simkl:tv", "TV Shows", "simkl", state = "Failed"))
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("dune", emptyList(), emptyList(), false, null, false, {}, {},
                        editQuery = {}, searchRows = rows)
                }
            }
        }
        try {
            frames(scene, 0)
            val nodes = nodes(scene)
            val resultTop = nodes.filter { text(it).contains("Returned title") }.minOf { it.boundsInRoot.top }
            val failedTops = nodes.filter { text(it).contains("This search provider could not respond") }.map { it.boundsInRoot.top }
            assertEquals(3, failedTops.size)
            assertTrue(failedTops.all { it > resultTop }, "Failed provider blocks must render below the providers that returned results")
        } finally { scene.close() }
    }

    @Test fun aFailedSearchOffersTheRetryThatIssuesTheSameQueryAgain() {
        var retried = 0
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("dune", emptyList(), emptyList(), false, "Your server took too long to answer this search. Try again.", false, {}, {},
                        editQuery = {}, retry = { retried++ })
                }
            }
        }
        try {
            frames(scene, 0)
            val nodes = nodes(scene)
            assertTrue(nodes.any { text(it).contains("took too long") })
            val retry = nodes.single { it.config.contains(SemanticsActions.OnClick) && text(it) == "Try again" }
            assertTrue(retry.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, retried)
        } finally { scene.close() }
    }

    @Test fun aQueryWhereEveryProviderFailedShowsOneFailureLineAndNoProviderBlocks() {
        val rows = listOf("Movies Search", "Series Search", "People Search Movies", "People Search Series")
            .map { SearchResultRow(it.lowercase().replace(' ', '-'), it, "stremio", state = "Failed") }
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("zzzqx", emptyList(), emptyList(), false, null, false, {}, {},
                        editQuery = {}, searchRows = rows)
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertEquals(1, texts.count { it == "This search provider could not respond. Try again." })
            assertFalse(texts.any { it == "Movies Search" || it == "People Search Series" })
            assertFalse(texts.any { it == "No matching titles" })
        } finally { scene.close() }
    }

    @Test fun providersThatOnlyCameBackEmptyStillSayNoMatchingTitles() {
        val rows = listOf(SearchResultRow("addon:1", "First provider", "stremio", addon_id = 1, state = "Empty"),
            SearchResultRow("addon:2", "Second provider", "stremio", addon_id = 2, state = "Empty"))
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("zzzqx", emptyList(), emptyList(), false, null, false, {}, {},
                        editQuery = {}, searchRows = rows)
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it == "First provider" })
        } finally { scene.close() }
    }

    @Test fun aProviderThatNeedsCredentialsShowsTheEmptyStateNotAPerProviderBlock() {
        val rows = listOf(
            SearchResultRow("movies-search", "Movies Search", "stremio", state = "Empty"),
            SearchResultRow("tv-shows", "TV Shows", "stremio", state = "Failed",
                message = "This add-on needs valid credentials to search."))
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("zzzqx", emptyList(), emptyList(), false, null, false, {}, {},
                        editQuery = {}, searchRows = rows)
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertEquals(1, texts.count { it == "No matching titles" })
            assertFalse(texts.any { it == "TV Shows" })
            assertFalse(texts.any { it.contains("could not respond") })
        } finally { scene.close() }
    }

    @Test fun oneGenuineFailureAmongWorkingProvidersShowsOnlyThatGroupsError() {
        val result = Card(MediaIdentity("result-id", "movie", emptyList()), provider_id = "addon:1", title = "Returned title", badges = emptyList())
        val rows = listOf(
            SearchResultRow("addon:1", "Cinemeta", "stremio", addon_id = 1, cards = listOf(result)),
            SearchResultRow("simkl:tv", "TV Shows", "simkl", state = "Failed"))
        val scene = ImageComposeScene(600, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("dog", emptyList(), emptyList(), false, null, false, {}, {},
                        editQuery = {}, searchRows = rows)
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.any { it == "Returned title" })
            assertEquals(1, texts.count { it == "This search provider could not respond. Try again." })
            assertTrue(texts.any { it == "Cinemeta" })
            assertTrue(texts.any { it == "TV Shows" })
            assertFalse(texts.any { it == "No matching titles" })
        } finally { scene.close() }
    }

    @Test fun idleSearchShowsRecentAndTrendingSearches() {
        var queried: String? = null
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("", emptyList(), emptyList(), false, null, false, { queried = it }, {}, editQuery = {},
                        recentSearches = listOf("monster", "naruto"), trendingSearches = listOf("The Whisper Man", "Mayday"))
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains("Recent"))
            assertTrue(texts.contains("monster"))
            assertTrue(texts.contains("naruto"))
            assertTrue(texts.contains("Trending searches"))
            assertTrue(texts.contains("The Whisper Man"))
            assertTrue(texts.contains("Mayday"))
            val recent = nodes(scene).single { it.config.contains(SemanticsActions.OnClick) && text(it) == "monster" }
            assertTrue(recent.config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals("monster", queried)
        } finally { scene.close() }
    }

    @Test fun idleDesktopSearchShowsRecentButNotTrending() {
        val scene = ImageComposeScene(1440, 1100, Density(1f)) {
            ViptvTheme {
                Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    SearchScreen("", emptyList(), emptyList(), false, null, true, {}, {}, editQuery = {},
                        recentSearches = listOf("monster"), trendingSearches = listOf("Mayday"))
                }
            }
        }
        try {
            frames(scene, 0)
            val texts = nodes(scene).map(::text)
            assertTrue(texts.contains("Recent"))
            assertTrue(texts.contains("monster"))
            assertFalse(texts.contains("Trending searches"))
            assertFalse(texts.contains("Mayday"))
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int) = repeat(20) {
        scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4)
    }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
