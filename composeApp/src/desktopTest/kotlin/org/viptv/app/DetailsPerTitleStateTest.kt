package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsPerTitleStateTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun series(id: String, title: String) = Card(MediaIdentity(id, "series", emptyList()), provider_id = "addon:21",
        title = title, badges = emptyList(), landscape = "https://images.example/$id.jpg", poster = "https://images.example/$id.jpg",
        description = "$title has a description.", metadata = CardMetadata(year = 2022, runtime_minutes = 25, genres = listOf("Action")))

    private fun DetailSnapshot.withEpisodes(count: Long): DetailSnapshot = copy(
        episodes = (1L..count).map { number ->
            item.copy(identity = MediaIdentity("${item.identity.id}:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
                parent_identity = item.identity, title = "Episode $number", description = null,
                metadata = CardMetadata(runtime_minutes = 25))
        },
        seasons = listOf(EpisodeSeason(1, count, 0)))

    private val first = DetailSnapshot(series("edgerunners", "Cyberpunk: Edgerunners"), episodes = emptyList()).withEpisodes(12)
        .let { it.copy(recommendations = listOf(series("obsession", "Obsession"))) }
    private val second = DetailSnapshot(series("oak-street", "The End of Oak Street"), episodes = emptyList()).withEpisodes(12)
        .let { it.copy(recommendations = listOf(series("still-life", "Still Life"))) }

    private fun episodeLabel(number: Long) = "$number. Episode $number"

    private inner class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false,
        val tablet: Boolean = false, val browser: Boolean = false, opening: DetailSnapshot? = first) : AutoCloseable {
        var detail by mutableStateOf(opening)
        var visiting by mutableStateOf(true)
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            val routes = rememberSaveableStateHolder()
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                if (visiting) routes.SaveableStateProvider("detail:profile") {
                    DetailsScreen(detail, busy = detail == null, error = null, back = {}, sources = {},
                        television = television, tablet = tablet, browser = browser)
                }
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

        fun merged(): List<SemanticsNode> {
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            return scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        }

        private fun scrollable() = merged().filter { it.config.contains(SemanticsActions.ScrollBy) &&
            it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            .maxByOrNull { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }

        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun shownTexts() = nodes().flatMap(::texts)
        fun node(text: String) = nodes().firstOrNull { text in texts(it) }
        fun top(text: String) = assertNotNull(node(text), "$name: \"$text\" is on screen: ${shownTexts()}").boundsInRoot.top
        fun gone(text: String) = node(text)?.let { it.boundsInRoot.bottom <= 0f } ?: true
        fun scrolled() = assertNotNull(scrollable(), "$name: the page scrolls: ${shownTexts()}")
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        fun click(text: String) {
            val target = assertNotNull(merged().firstOrNull { it.config.contains(SemanticsActions.OnClick) && text in texts(it) },
                "$name: \"$text\" is clickable: ${shownTexts()}")
            assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke(), "$name: \"$text\" took the tap")
        }
        fun scrollDown(by: Float) {
            val target = assertNotNull(scrollable(), "$name: the page scrolls")
            target.config[SemanticsActions.ScrollBy].action!!.invoke(0f, by)
        }
        fun episodeOnScreen(): String? = shownTexts().firstOrNull { it.matches(Regex("[0-9]+\\. Episode [0-9]+")) }
        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val layouts = listOf<Pair<String, (DetailSnapshot) -> Layout>>(
        "phone" to { opening -> Layout("phone", 390, 844, opening = opening) },
        "tablet" to { opening -> Layout("tablet", 1280, 800, tablet = true, opening = opening) },
        "desktop" to { opening -> Layout("desktop", 1280, 800, opening = opening) },
        "browser" to { opening -> Layout("browser", 1280, 900, browser = true, opening = opening) },
        "tv" to { opening -> Layout("tv", 1920, 1080, television = true, opening = opening) },
    )

    @Test fun everyDetailsListRemembersTheTitleOnScreen() {
        val pages = java.io.File("src/commonMain/kotlin/org/viptv/app/features/details")
        assertTrue(pages.isDirectory, "run from the composeApp module")
        val keepsItsOwn = setOf("DetailPageState.kt", "TvEpisodeShelf.kt")
        val found = pages.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "kt" && it.name !in keepsItsOwn }
            .filter { file -> file.readLines().any { "rememberLazyListState" in it || "LazyListState.Saver" in it } }
            .map { it.name }
        assertEquals(emptyList(), found, "details lists scroll through rememberDetailListState()")
    }

    @Test fun anotherTitleOpensWhereAFreshOpenSits() = onEdt {
        for ((name, layout) in layouts) {
            val fresh = layout(second).use { page -> page.settle(); page.top(second.item.title) }
            val swapped = layout(first).use { page ->
                page.settle()
                page.scrollDown(4000f)
                page.settle()
                assertTrue(page.scrolled() > 0f, "${page.name}: the first title is scrolled before the swap")
                page.detail = second
                page.settle()
                if (page.name == "phone") assertEquals(1, page.shownTexts().count { it == second.item.title },
                    "the pinned header stays asleep for a title that opens at the top")
                page.top(second.item.title)
            }
            assertTrue(fresh < (if (name == "tv") 500f else 400f), "$name: a fresh title opens at the top: ${fresh}px")
            assertEquals(fresh, swapped, "$name: the second title opens where a fresh open sits")
        }
    }

    @Test fun theSectionTabComesBackFromThePlayer() = onEdt {
        Layout("phone", 390, 844).use { page ->
            page.settle()
            page.click("More like this")
            page.settle()
            assertNotNull(page.node("Obsession"), "the related title is drawn: ${page.shownTexts()}")
            assertNull(page.node(episodeLabel(1)), "the episodes are not")
            page.visiting = false
            page.settle()
            page.visiting = true
            page.settle()
            assertNotNull(page.node("Obsession"), "Back lands on the section the viewer left: ${page.shownTexts()}")
            assertNull(page.node(episodeLabel(1)), "the tab comes back with the page")
        }
    }

    @Test fun theScrollComesBackFromThePlayer() = onEdt {
        Layout("phone", 390, 844).use { page ->
            page.settle()
            page.scrollDown(1400f)
            page.settle()
            val offset = page.scrolled()
            assertTrue(offset > 0f, "the page scrolled: $offset")
            assertTrue(page.gone(first.item.description!!), "the hero is scrolled away")
            val reached = assertNotNull(page.episodeOnScreen(), "an episode is on screen: ${page.shownTexts()}")
            page.visiting = false
            page.settle()
            page.visiting = true
            page.settle()
            assertTrue(abs(page.scrolled() - offset) < 2f, "Back restores the offset: $offset against ${page.scrolled()}")
            assertNotNull(page.node(reached), "Back lands on $reached again: ${page.shownTexts()}")
            assertTrue(page.gone(first.item.description!!), "the hero stays scrolled away")
        }
    }
}