package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.PendingMutationUi
import org.viptv.app.features.home.CANCEL_LABEL
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.CardMenu
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.MutationTarget
import org.viptv.app.state.PendingMutations
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class MyListFailureSurfacesUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private companion object {
        val message = "Could not save this change. Try again."
        val captures = File("/tmp/viptv-1034-mylist-failure").apply { deleteRecursively(); mkdirs() }
    }

    private data class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false,
        val tablet: Boolean = false, val browser: Boolean = false, val desktopChrome: Boolean = false) {
        val menuScale get() = if (television) 1.7f else 1f
    }

    private val layouts = listOf(
        Layout("phone", 390, 844),
        Layout("tablet", 1280, 800, tablet = true),
        Layout("desktop", 1440, 1000, desktopChrome = true),
        Layout("web", 1440, 1000, browser = true),
        Layout("tv", 1920, 1080, television = true),
    )

    private val hero = PreviewData.hero
    private val rowCard = PreviewData.home.rows.first { it.cards.isNotEmpty() }.cards.first()
    private val heroTarget = saveTarget(hero)
    private val cardTarget = saveTarget(rowCard)
    private val otherCard = rowCard.copy(identity = MediaIdentity("other-card", "movie", emptyList()), title = "Another title")

    private var retried: MutationTarget? = null

    private fun feedback(target: MutationTarget? = null, pending: Boolean = false): PendingMutationUi {
        val failures = when {
            target == null -> PendingMutations()
            pending -> PendingMutations().starting(target)
            else -> PendingMutations().failed(target, message)
        }
        return PendingMutationUi(failures, retry = { retried = it })
    }

    private inner class Page(val name: String, private val layout: Layout, ui: PendingMutationUi,
        content: @Composable (Layout) -> Unit) : AutoCloseable {
        private var frame = 0L
        val scene = ImageComposeScene(layout.width, layout.height, Density(1f)) {
            ViptvTheme(layout.television, pointer = !layout.television) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    CompositionLocalProvider(LocalPendingMutation provides ui) { content(layout) }
                }
            }
        }

        suspend fun settle() {
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
            delay(200)
            repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
        }

        fun nodes(merged: Boolean = true): List<SemanticsNode> =
            scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }

        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun label(node: SemanticsNode) = (texts(node) +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")

        fun failureLines() = nodes(merged = false).filter { message in texts(it) }
        fun spinning() = nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }

        fun retry(): SemanticsNode = assertNotNull(nodes().firstOrNull {
            it.config.contains(SemanticsActions.OnClick) && "Retry" in label(it)
        }, "$name: a Retry control is on screen: " + nodes().map(::label))

        fun capture(surface: String) = scene.render(frame++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(captures, "$surface-${layout.name}.png").writeBytes(it.bytes) }
        }

        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun home(layout: Layout, ui: PendingMutationUi) = Page("home/${layout.name}", layout, ui) { l ->
        HomeScreen(PreviewData.home, television = l.television, open = {}, retry = {}, desktopChrome = l.desktopChrome,
            play = {}, tablet = l.tablet, browser = l.browser, save = {}, saved = { false }, heroRotates = false)
    }

    private fun cardGrid(layout: Layout, ui: PendingMutationUi) = Page("cards/${layout.name}", layout, ui) { l ->
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (l.television) {
                TvCatalogCard(rowCard, scale = 1f, portrait = false, focused = {}, open = {})
                TvCatalogCard(otherCard, scale = 1f, portrait = false, focused = {}, open = {})
            } else {
                val width = if (layout.name == "phone") 160 else 240
                MediaCard(rowCard, width = width, open = {})
                MediaCard(otherCard, width = width, open = {})
            }
        }
    }

    private fun titleMenu(layout: Layout, ui: PendingMutationUi) = Page("menu/${layout.name}", layout, ui) { l ->
        CardMenu(rowCard, saved = false, removal = null, actions = CardActions(play = {}, toggleSave = {}, isSaved = { false }),
            onDismiss = {}, onPlay = {}, onInfo = {}, onToggleSave = {}, scale = l.menuScale, television = l.television)
    }

    @Test fun everyLayoutSpinsTheHeroControlWhileTheWriteRuns() = onEdt {
        for (layout in layouts) home(layout, feedback(heroTarget, pending = true)).use { page ->
            page.settle()
            assertTrue(page.spinning(), "${layout.name}: the hero's My List control spins while the write runs")
            assertTrue(page.failureLines().isEmpty(), "${layout.name}: no failure line while the write runs")
        }
    }

    @Test fun everyLayoutShowsTheHeroFailureAndRetriesTheSameWrite() = onEdt {
        for (layout in layouts) {
            retried = null
            home(layout, feedback(heroTarget)).use { page ->
                page.settle()
                assertEquals(1, page.failureLines().size,
                    "${layout.name}: one failure line under the hero's save control: " + page.nodes(merged = false).map { page.label(it) })
                page.capture("home-hero")
                val retry = page.retry()
                assertTrue(retry.config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: Retry takes the press")
                assertEquals(heroTarget, retried, "${layout.name}: Retry runs the same My List write")
            }
        }
    }

    @Test fun everyLayoutShowsTheCardFailureOnThatCardOnly() = onEdt {
        for (layout in layouts) {
            retried = null
            // A TV window tall enough to show the first shelf's captions, where the card's line sits.
            home(if (layout.television) layout.copy(height = 1440) else layout, feedback(cardTarget)).use { page ->
                page.settle()
                val lines = page.failureLines()
                assertEquals(1, lines.size, "${layout.name}: one failure line, on the card that failed: " + page.nodes(merged = false).map { page.label(it) })
                assertTrue(page.nodes(merged = false).none { message in page.texts(it) && "Another title" in page.texts(it) },
                    "${layout.name}: the neighbouring card draws nothing")
                page.capture("home-card")
                if (!layout.television) {
                    val retry = page.retry()
                    assertTrue(retry.config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the card's Retry takes the press")
                    assertEquals(cardTarget, retried, "${layout.name}: the card's Retry runs the same My List write")
                }
            }
        }
    }

    @Test fun aCardGridDrawsTheFailureOnTheCardThatFailed() = onEdt {
        for (layout in layouts) {
            retried = null
            cardGrid(layout, feedback(cardTarget)).use { page ->
                page.settle()
                assertEquals(1, page.failureLines().size, "${layout.name}: one failure line in the grid: " + page.nodes(merged = false).map { page.label(it) })
                page.capture("grid")
                if (!layout.television) {
                    assertTrue(page.retry().config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the grid card's Retry takes the press")
                    assertEquals(cardTarget, retried, "${layout.name}: the grid card's Retry runs the same My List write")
                }
            }
        }
    }

    @Test fun everyLayoutShowsTheTitleMenusFailureAndRetriesTheSameWrite() = onEdt {
        for (layout in layouts) {
            retried = null
            titleMenu(layout, feedback(cardTarget)).use { page ->
                page.settle()
                assertEquals(1, page.failureLines().size, "${layout.name}: one failure line in the title menu: " + page.nodes(merged = false).map { page.label(it) })
                assertEquals(true, page.nodes().any { it.config.contains(SemanticsActions.OnClick) && CANCEL_LABEL in page.label(it) },
                    "${layout.name}: the menu is still the title menu")
                page.capture("menu")
                assertTrue(page.retry().config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the menu's Retry takes the press")
                assertEquals(cardTarget, retried, "${layout.name}: the menu's Retry runs the same My List write")
            }
        }
    }
}