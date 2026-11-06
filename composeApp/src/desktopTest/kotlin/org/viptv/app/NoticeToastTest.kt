@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import org.viptv.app.components.*
import org.viptv.app.state.AppNotice
import org.viptv.app.state.NoticeAction
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

class NoticeToastTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()).joinToString(" ")
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::label)
    private fun clickable(scene: ImageComposeScene, text: String) = nodes(scene).firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && label(it).contains(text)
    }
    private fun tap(scene: ImageComposeScene, text: String) =
        assertTrue(requireNotNull(clickable(scene, text)) { "$text is not on screen" }
            .config[SemanticsActions.OnClick].action?.invoke() == true)
    private fun frames(scene: ImageComposeScene, count: Int = 10) = repeat(count) { scene.render(it * 16_666_667L).close() }

    private fun notice(action: NoticeAction? = null) = AppNotice(
        if (action == null) "Added to My List" else "Removed from My List", action, serial = 1L)

    private fun scene(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }

    @Test fun thePhoneNoticeSitsAboveTheNavWithItsUndo() {
        var undone = 0
        val shown = scene(390, 844) {
            NoticeToast(notice(NoticeAction.Undo), television = false, phone = true, aboveNav = true,
                undo = { undone++ }, dismiss = {})
        }
        try {
            frames(shown)
            assertTrue(texts(shown).any { it.contains("Removed from My List") })
            assertTrue(texts(shown).any { it.contains("Undo") }, "the action sits on the notice's own line")
            tap(shown, "Undo")
            assertEquals(1, undone)
        } finally { shown.close() }
    }

    @Test fun aConfirmationCarriesNoAction() {
        val shown = scene(1280, 900) {
            NoticeToast(notice(), television = false, phone = false, aboveNav = false, undo = {}, dismiss = {})
        }
        try {
            frames(shown)
            assertTrue(texts(shown).any { it.contains("Added to My List") })
            assertNull(clickable(shown, "Undo"), "a notice with nothing to undo shows text only")
        } finally { shown.close() }
    }

    @Test fun theNoticeDismissesItselfOnItsOwnClock() {
        var dismissed = 0
        val shown = scene(1280, 900) {
            NoticeToast(notice(NoticeAction.Undo), television = false, phone = false, aboveNav = false,
                undo = {}, dismiss = { dismissed++ }, durationMillis = 400)
        }
        try {
            assertEquals(0, dismissed, "it stays while the viewer can still reach it")
            repeat(600) { if (dismissed == 0) frames(shown, 1) }
            assertEquals(1, dismissed, "the toast dismisses itself once its clock is up")
        } finally { shown.close() }
    }
}
