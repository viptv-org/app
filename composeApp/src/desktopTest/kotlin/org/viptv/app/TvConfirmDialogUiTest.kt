package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
import org.viptv.app.components.ListRemovalConfirmation
import org.viptv.app.features.continuewatching.REMOVE_CONTINUE_LABEL
import org.viptv.app.features.settings.TvConfirmDialog
import org.viptv.app.features.settings.TvDialogPanelTag
import org.viptv.app.features.settings.TvLeaveConfirmation
import org.viptv.app.features.settings.TvNoticeDialog
import org.viptv.app.features.library.REMOVE_LIBRARY_LABEL
import org.viptv.app.features.library.TvLibraryScreen
import org.viptv.app.connection.defaultLibraryQuery
import org.viptv.engine.wire.Availability
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.LibrarySnapshot
import org.viptv.engine.wire.Scope
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvConfirmDialogUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private companion object {
        val captures = File("/tmp/viptv-1068-tv-dialogs").apply { deleteRecursively(); mkdirs() }
    }

    private val card = PreviewData.home.rows.first { it.cards.isNotEmpty() }.cards.first()

    private inner class Panel(val name: String, content: @Composable () -> Unit) : AutoCloseable {
        private var clock = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        }

        fun tick() { scene.render(clock++ * 16_666_667L).close() }

        suspend fun settle(count: Int = 24) {
            repeat(count) { tick(); yield() }
            delay(120)
            repeat(8) { tick(); yield() }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        fun label(node: SemanticsNode) = (texts(node) +
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ").trim()
        fun labels() = nodes().map(::label).filter { it.isNotEmpty() }
        fun panel() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == TvDialogPanelTag }
        fun action(text: String) = nodes().firstOrNull {
            it.config.contains(SemanticsActions.OnClick) && label(it) == text
        }
        fun focused(): String? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::label)

        suspend fun hold() {
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyDown))
            repeat(45) { delay(20); tick() }
            scene.sendKeyEvent(KeyEvent(Key.DirectionCenter, KeyEventType.KeyUp))
            tick()
        }

        fun capture() = scene.render(clock++ * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(captures, "$name.png").writeBytes(it.bytes) }
        }

        override fun close() = scene.close()
    }

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    @Test fun theListRemovalConfirmationIsTheTvPanelWithCancelFirst() = onEdt {
        var confirmed = 0
        var dismissed = 0
        Panel("list-removal") {
            ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = { confirmed++ }, dismiss = { dismissed++ },
                television = true, scale = 1f)
        }.use { panel ->
            panel.settle()
            assertNotNull(panel.panel(), "the confirmation draws TvDialogPanel: ${panel.labels()}")
            assertTrue(panel.labels().contains("$REMOVE_CONTINUE_LABEL?"), "the panel names the removal: ${panel.labels()}")
            assertNotNull(panel.action("Remove"), "Remove is a TV action")
            assertNotNull(panel.action("Cancel"), "Cancel is a TV action")
            assertEquals("Cancel", panel.focused(), "Cancel takes focus first, as §8 asks on TV")
            panel.capture()
            assertTrue(panel.action("Remove")!!.config[SemanticsActions.OnClick].action!!.invoke(), "Remove takes the press")
            assertEquals(1, confirmed)
            assertEquals(0, dismissed)
        }
    }

    @Test fun theSignOutConfirmationIsTheTvPanel() = onEdt {
        var confirmed = 0
        Panel("sign-out") { TvLeaveConfirmation(local = false, scale = 1f, confirm = { confirmed++ }, dismiss = {}) }.use { panel ->
            panel.settle()
            assertNotNull(panel.panel(), "sign-out draws TvDialogPanel")
            assertEquals("Cancel", panel.focused(), "Cancel takes focus first")
            assertNotNull(panel.action("Sign out"))
            panel.capture()
            panel.action("Sign out")!!.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, confirmed)
        }
    }

    @Test fun aNoticeIsTheTvPanelWithItsOneActionFocused() = onEdt {
        var dismissed = 0
        Panel("notice") { TvNoticeDialog(1f, "Profile PIN", "PIN recovery is not available for this profile.", onDismiss = { dismissed++ }) }
            .use { panel ->
                panel.settle()
                assertNotNull(panel.panel(), "the notice draws TvDialogPanel")
                assertEquals("OK", panel.focused(), "the notice's one action is focused")
                panel.capture()
                panel.action("OK")!!.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(1, dismissed)
            }
    }

    @Test fun thePanelWalksItsActionsWithTheDpadAndClosesOnBack() = onEdt {
        var dismissed = 0
        Panel("walk") {
            TvConfirmDialog(1f, "Remove this title?", "It leaves this list.", confirm = "Remove", danger = true,
                onConfirm = {}, onDismiss = { dismissed++ })
        }.use { panel ->
            panel.settle()
            assertEquals("Cancel", panel.focused())
            panel.scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(
                androidx.compose.ui.input.key.Key.DirectionDown, androidx.compose.ui.input.key.KeyEventType.KeyDown))
            panel.settle(8)
            assertEquals("Remove", panel.focused(), "Down reaches the other action inside the panel")
            panel.scene.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(
                androidx.compose.ui.input.key.Key.Back, androidx.compose.ui.input.key.KeyEventType.KeyDown))
            panel.settle(8)
            assertEquals(1, dismissed, "Back closes the panel")
        }
    }

    @Test fun theMyListRemovalOnTvDrawsThePanelOverThePage(): Unit = runBlocking {
        val posters = PreviewData.posters
        val library = LibrarySnapshot(Scope("t", "t", "t", 1), 1, defaultLibraryQuery(), posters, complete = true,
            availability = Availability.AVAILABLE)
        var removed: Card? = null
        Panel("my-list-removal") {
            TvLibraryScreen(posters, available = true, open = {}, home = {}, snapshot = library, remove = { removed = it })
        }.use { page ->
            page.settle()
            // tv-my-list-posters.md: a 700 ms hold on a card opens the My List options.
            page.hold()
            page.settle(30)
            val remove = assertNotNull(page.action(REMOVE_LIBRARY_LABEL), "the card's hold menu offers its removal: ${page.labels()}")
            assertTrue(remove.config[SemanticsActions.OnClick].action!!.invoke(), "the removal opens its confirmation")
            page.settle(30)
            assertNotNull(page.panel(), "the confirmation is the TV panel: ${page.labels()}")
            assertTrue(page.labels().contains("$REMOVE_LIBRARY_LABEL?"), "the panel asks the question: ${page.labels()}")
            assertNotNull(page.action("Cancel"), "the panel draws Cancel")
            page.capture()
            page.action("Remove")!!.config[SemanticsActions.OnClick].action!!.invoke()
            page.settle()
            assertEquals(posters.first(), removed, "Remove asks the page for that card")
            assertNull(page.panel(), "the panel closes")
        }
    }

    @Test fun thePhoneLayoutKeepsItsMaterialDialog() = onEdt {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Box(Modifier.fillMaxSize().background(Palette.background)) {
                ListRemovalConfirmation(card, REMOVE_CONTINUE_LABEL, confirm = {}, dismiss = {})
            } }
        }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close() }
            val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            assertNull(nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == TvDialogPanelTag },
                "the phone keeps its Material dialog")
            assertTrue(nodes.any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "$REMOVE_CONTINUE_LABEL?" } },
                "the phone dialog still names the removal")
        } finally { scene.close() }
    }
}