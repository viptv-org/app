package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
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
import org.viptv.app.features.continuewatching.REMOVE_CONTINUE_LABEL
import org.viptv.app.features.home.CANCEL_LABEL
import org.viptv.app.features.home.CHOOSE_SOURCE_LABEL
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.features.home.MARK_WATCHED_LABEL
import org.viptv.app.features.home.WATCH_FROM_START_LABEL
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.HomeSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ContinueWatchingRemovalUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private companion object {
        val captures = File("/tmp/viptv-1041-cw-removal").apply { deleteRecursively(); mkdirs() }
        const val HoldMillis = 700L
    }

    private data class Layout(val name: String, val width: Int, val height: Int, val television: Boolean = false,
        val tablet: Boolean = false, val browser: Boolean = false, val desktopChrome: Boolean = false, val pointer: Boolean = false)

    private val layouts = listOf(
        Layout("phone", 390, 844),
        Layout("tablet", 1280, 800, tablet = true),
        Layout("desktop", 1440, 1000, desktopChrome = true, pointer = true),
        Layout("web", 1440, 1000, browser = true, pointer = true),
        Layout("tv", 1920, 1080, television = true),
    )

    private val continueRow = PreviewData.home.rows.first { it.id == "continue" }
    private val continueCard = continueRow.cards.first()
    private val episode = continueCard.copy(identity = continueCard.identity.copy(id = "watch-0:1:2", episode = EpisodeCoordinates("aired", 1, 2)))
    private val catalogueCard = PreviewData.home.rows.last { it.id != "continue" }.cards.first()
    private val withEpisode = PreviewData.home.copy(rows = PreviewData.home.rows.map {
        if (it.id == "continue") it.copy(cards = listOf(episode) + it.cards.drop(1)) else it
    })
    private val catalogueOnly = PreviewData.home.copy(rows = PreviewData.home.rows.filter { it.id != "continue" })

    private var removed: Card? = null
    private val actions = CardActions(play = {}, toggleSave = {}, isSaved = { false }, chooseSource = {},
        playFromStart = {}, markWatched = {})

    private inner class Page(val layout: Layout, home: HomeSnapshot = PreviewData.home) : AutoCloseable {
        private var clock = 0L
        val scene = ImageComposeScene(layout.width, layout.height, Density(1f)) {
            ViptvTheme(layout.television, pointer = layout.pointer) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    CompositionLocalProvider(LocalPendingMutation provides PendingMutationUi(), LocalCardActions provides actions) {
                        HomeScreen(home, television = layout.television, open = {}, retry = {},
                            desktopChrome = layout.desktopChrome, play = {}, tablet = layout.tablet, browser = layout.browser,
                            save = {}, saved = { false }, heroRotates = false,
                            removeContinueWatching = { card -> removed = card })
                    }
                }
            }
        }

        fun tick() { scene.render(clock++ * 16_666_667L).close() }

        suspend fun settle(count: Int = 24) {
            repeat(count) { tick(); yield() }
            delay(120)
            repeat(8) { tick(); yield() }
        }

        suspend fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 8_000_000_000L
            while (!condition()) {
                check(System.nanoTime() < deadline) { "$what timed out: " + labels() }
                tick()
                delay(15)
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun label(node: SemanticsNode) = (texts(node) +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ").trim()
        fun labels() = nodes().map(::label)

        fun clickable(text: String, exact: Boolean = true): SemanticsNode? = nodes().firstOrNull {
            it.config.contains(SemanticsActions.OnClick) && if (exact) label(it) == text else text in label(it)
        }

        fun tap(text: String) = assertTrue(
            requireNotNull(clickable(text)) { "“$text” is not on screen: " + labels() }
                .config[SemanticsActions.OnClick].action?.invoke() == true, "“$text” takes the press")

        fun at(title: String): Offset = nodes().first { title in label(it) && it.boundsInRoot.width > 0 }.boundsInRoot.center

        fun inMenu(menuTitle: String, text: String): SemanticsNode? {
            val menu = nodes().firstOrNull { label(it) == "$menuTitle actions" }?.boundsInRoot ?: return null
            return nodes().firstOrNull {
                it.config.contains(SemanticsActions.OnClick) && label(it) == text && menu.contains(it.boundsInRoot.center)
            }
        }

        fun focusedLabel(): String? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::label)

        fun focusCard(title: String): Boolean {
            val node = nodes().firstOrNull { title in label(it) && it.config.contains(SemanticsActions.RequestFocus) } ?: return false
            return node.config[SemanticsActions.RequestFocus].action?.invoke() ?: false
        }

        suspend fun openMenu(title: String) {
            when {
                layout.television -> {
                    assertTrue(focusCard(title), "the D-pad reaches “$title”")
                    settle(12)
                    assertTrue(focusedLabel()?.contains(title) == true, "the D-pad is on “$title”: ${focusedLabel()}")
                    scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown))
                    await("the hold opens $title's title menu") { openMenus().isNotEmpty() }
                    scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
                }
                layout.pointer -> {
                    val point = at(title)
                    scene.sendPointerEvent(PointerEventType.Move, point, type = PointerType.Mouse)
                    scene.sendPointerEvent(PointerEventType.Press, point, type = PointerType.Mouse,
                        buttons = PointerButtons(isSecondaryPressed = true))
                    scene.sendPointerEvent(PointerEventType.Release, point, type = PointerType.Mouse, buttons = PointerButtons())
                }
                else -> assertTrue(
                    requireNotNull(clickable("More actions", exact = false)) { "no card overflow target: " + labels() }
                        .config[SemanticsActions.OnClick].action?.invoke() == true, "the card overflow opens the menu")
            }
        }

        fun openMenus(): List<String> = nodes().map(::label).filter { it.endsWith(" actions") && it != "More actions" }

        fun capture(name: String) {
            scene.render(clock++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { File(captures, "$name-${layout.name}.png").writeBytes(it.bytes) }
            }
        }

        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    @Test fun everyLayoutOffersTheShelfRemovalAndConfirmsIt() = onEdt {
        for (layout in layouts) {
            removed = null
            Page(layout).use { page ->
                page.settle()
                page.openMenu(continueCard.title)
                page.settle(12)
                assertEquals(listOf("${continueCard.title} actions"), page.openMenus(),
                    "${layout.name}: the Continue Watching card's title menu opened: " + page.labels())
                val entry = assertNotNull(page.inMenu(continueCard.title, REMOVE_CONTINUE_LABEL),
                    "${layout.name}: the menu offers $REMOVE_CONTINUE_LABEL: " + page.labels())
                page.capture("menu")
                assertTrue(entry.config[SemanticsActions.OnClick].action!!.invoke(), "${layout.name}: the removal takes the press")
                page.settle(12)
                assertTrue(page.nodes().any { "“${continueCard.title}” leaves this list" in page.texts(it).joinToString(" ") },
                    "${layout.name}: the shared confirmation names the title: " + page.labels())
                page.capture("confirm")
                page.tap("Remove")
                page.settle(12)
                assertEquals(continueCard, removed, "${layout.name}: confirming asks the shelf's own removal for that card")
            }
        }
    }

    @Test fun everyLayoutKeepsTheRemovalBetweenPlayFromStartAndTheSaveToggle() = onEdt {
        for (layout in layouts) {
            Page(layout, withEpisode).use { page ->
                page.settle()
                page.openMenu(episode.title)
                page.settle(12)
                val tops = listOf(WATCH_FROM_START_LABEL, REMOVE_CONTINUE_LABEL, "Add to My List").map { entry ->
                    assertNotNull(page.inMenu(episode.title, entry), "${layout.name}: $entry is in the menu: " + page.labels())
                    page.inMenu(episode.title, entry)!!.boundsInRoot.top
                }
                assertEquals(tops.sorted(), tops, "${layout.name}: $REMOVE_CONTINUE_LABEL sits between $WATCH_FROM_START_LABEL and Add to My List")
            }
        }
    }

    @Test fun aCatalogShelfNeverOffersTheContinueWatchingRemoval() = onEdt {
        for (layout in layouts) {
            Page(layout, catalogueOnly).use { page ->
                page.settle()
                page.openMenu(catalogueCard.title)
                page.settle(12)
                assertEquals(listOf("${catalogueCard.title} actions"), page.openMenus(),
                    "${layout.name}: the catalog card's menu opened: " + page.labels())
                assertNotNull(page.inMenu(catalogueCard.title, CHOOSE_SOURCE_LABEL), "${layout.name}: the menu keeps its own actions")
                assertNotNull(page.inMenu(catalogueCard.title, MARK_WATCHED_LABEL))
                assertNotNull(page.inMenu(catalogueCard.title, CANCEL_LABEL))
                assertNull(page.inMenu(catalogueCard.title, REMOVE_CONTINUE_LABEL),
                    "${layout.name}: a catalog card never offers the Continue Watching removal")
            }
        }
    }
}