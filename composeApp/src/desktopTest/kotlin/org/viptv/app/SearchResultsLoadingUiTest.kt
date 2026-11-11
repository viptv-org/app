package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.components.ListsLoading
import org.viptv.app.connection.SearchResultRow
import org.viptv.app.features.search.SearchScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SearchResultsLoadingUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val scopes = listOf("All", "Movies", "Series", "Anime", "Channels")
    private val answering = listOf(SearchResultRow("addon:1", "Fast catalog", "stremio", addon_id = 1, state = "Loading"))
    private fun card(title: String) = Card(MediaIdentity(title.lowercase(), "movie", emptyList()),
        provider_id = "stremio", title = title, badges = emptyList())

    @Test fun phoneTabletAndWebDrawTheirOwnPosterSkeletonWhileTheFirstAnswerIsOut() {
        for (width in listOf(390, 768, 1280, 1440)) {
            val scene = render(width, 900) {
                SearchScreen("dune", emptyList(), emptyList(), busy = true, error = null, desktop = width >= 1280, query = {}, open = {},
                    editQuery = {}, searchRows = answering)
            }
            try {
                assertEquals(1, skeletons(scene), "$width: the page draws its results skeleton")
                val texts = texts(scene)
                assertFalse(texts.any { it == "No matching titles" }, "$width: no empty state while the request is open")
                assertFalse(texts.any { it == "Searching…" }, "$width: no status line stands in for the skeleton")
                for (scope in scopes) assertTrue(texts.any { it == scope }, "$width: the scope chips stay while a query is asked")
            } finally { scene.close() }
        }
    }

    @Test fun aBlankQueryDrawsNoScopeChips() {
        for (width in listOf(390, 1280)) {
            val scene = render(width, 900) {
                SearchScreen("", emptyList(), emptyList(), busy = false, error = null, desktop = width >= 1280, query = {}, open = {},
                    editQuery = {}, recentSearches = listOf("dune"))
            }
            try {
                val texts = texts(scene)
                for (scope in scopes) assertFalse(texts.any { it == scope }, "$width: nothing is there to filter")
                assertTrue(texts.any { it == "Recent" }, "$width: the idle page is still drawn")
            } finally { scene.close() }
        }
    }

    @Test fun theResultsAlreadyShownStayUntilTheNewOnesLand() {
        for (width in listOf(390, 1280)) {
            val scene = render(width, 900) {
                SearchScreen("dune part two", listOf(card("Dune")), emptyList(), busy = true, error = null, desktop = width >= 1280,
                    query = {}, open = {}, editQuery = {}, searchRows = answering)
            }
            try {
                assertTrue(texts(scene).any { it.contains("Dune") }, "$width: the previous results stay on the page")
                assertEquals(0, skeletons(scene), "$width: a page with results keeps them instead of a skeleton")
            } finally { scene.close() }
        }
    }

    private fun render(width: Int, height: Int, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        repeat(20) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(scene: ImageComposeScene) = nodes(scene).mapNotNull {
        it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text }
    }
    private fun skeletons(scene: ImageComposeScene) = nodes(scene).count { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == ListsLoading } == true
    }
}