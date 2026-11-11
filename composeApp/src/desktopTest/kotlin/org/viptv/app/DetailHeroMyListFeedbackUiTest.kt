package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.PendingMutationUi
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.state.MutationTarget
import org.viptv.app.state.PendingMutations
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.EpisodeSeason
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailHeroMyListFeedbackUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val message = "Could not save this change. Try again."

    private val series = Card(MediaIdentity("edgerunners", "series", emptyList()), provider_id = "addon:21",
        title = "Cyberpunk: Edgerunners", badges = listOf("4K"),
        landscape = "https://images.example/edgerunners.jpg", poster = "https://images.example/edgerunners.jpg",
        description = "A street kid tries to survive in a city obsessed with technology and body modification.",
        metadata = CardMetadata(year = 2026, runtime_minutes = 25, genres = listOf("Anime", "Action"), rating = 8.6,
            cast = listOf("Kiyoshi Kurosawa", "Aoi Yamada")))

    private val detail = DetailSnapshot(series, episodes = (1L..3L).map { number ->
        series.copy(identity = MediaIdentity("edgerunners:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
            parent_identity = series.identity, title = "Episode $number", description = null,
            metadata = CardMetadata(runtime_minutes = 25))
    }, seasons = listOf(EpisodeSeason(1, 3, 0)),
        recommendations = listOf(series.copy(identity = MediaIdentity("obsession", "series", emptyList()), title = "Obsession")))

    private val target = saveTarget(series)
    private var retried: MutationTarget? = null

    private fun feedback(pending: Boolean = false, failure: String? = null) = PendingMutationUi(
        when {
            failure != null -> PendingMutations().failed(target, failure)
            pending -> PendingMutations().starting(target)
            else -> PendingMutations()
        }, retry = { retried = it })

    private inner class Page(val name: String, width: Int, height: Int, television: Boolean = false,
        tablet: Boolean = false, browser: Boolean = false, ui: PendingMutationUi) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                CompositionLocalProvider(LocalPendingMutation provides ui) {
                    DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, television = television,
                        tablet = tablet, browser = browser)
                }
            }
        } }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun nodes(): List<SemanticsNode> {
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            return scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        }

        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun shownTexts() = nodes().flatMap(::texts)
        fun described(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        fun retry(): SemanticsNode = assertNotNull(nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && ("Retry" in texts(node) || "Retry" in described(node))
        }, "$name: a Retry control is on screen: ${shownTexts()} / " + nodes().map { described(it) })
        fun spinning() = nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }
        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val layouts = listOf<Pair<String, (PendingMutationUi) -> Page>>(
        "phone" to { ui -> Page("phone", 390, 844, ui = ui) },
        "tablet" to { ui -> Page("tablet", 1280, 800, tablet = true, ui = ui) },
        "desktop" to { ui -> Page("desktop", 1280, 800, ui = ui) },
        "browser" to { ui -> Page("browser", 1280, 900, browser = true, ui = ui) },
        "tv" to { ui -> Page("tv", 1920, 1080, television = true, ui = ui) },
    )

    @Test fun everyLayoutSpinsTheMyListControlWhileTheWriteRuns() = onEdt {
        for ((name, layout) in layouts) {
            layout(feedback(pending = true)).use { page ->
                page.settle()
                assertTrue(page.spinning(), "$name: the My List control spins while the write runs")
                assertNull(page.nodes().firstOrNull { node -> page.texts(node).any { it == message } },
                    "$name: no failure line while the write runs")
            }
        }
    }

    @Test fun everyLayoutShowsTheFailureAndRetriesTheSameWrite() = onEdt {
        for ((name, layout) in layouts) {
            retried = null
            layout(feedback(failure = message)).use { page ->
                page.settle()
                assertTrue(page.shownTexts().contains(message), "$name: the shared failure line is on screen: ${page.shownTexts()}")
                assertTrue(!page.spinning(), "$name: the control is back to its own state after the revert")
                if (name in listOf("phone", "desktop")) assertTrue(page.nodes().any { "Add to My List" in page.described(it) },
                    "$name: the My List control carries its own name again, not the failure")
                val retry = page.retry()
                assertTrue(retry.config[SemanticsActions.OnClick].action!!.invoke(), "$name: Retry takes the tap")
                assertEquals(target, retried, "$name: Retry runs the same My List write")
            }
        }
    }
}