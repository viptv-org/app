@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.components.CrashReportNotice
import org.viptv.app.components.TvAction
import org.viptv.app.components.showsCrashNotice
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvCrashNoticeFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Fixture(var sent: Int = 0, var discarded: Int = 0) : AutoCloseable {
        private var focusManager: androidx.compose.ui.focus.FocusManager? = null
        private var frame = 0L
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            val primary = remember { FocusRequester() }
            Box(Modifier.fillMaxSize()) {
                TvAction(Modifier.align(Alignment.BottomStart).padding(start = 1108.dp, bottom = 100.dp).size(260.dp, 72.dp).testTag("primary-action"),
                    1f, requester = primary, activate = {}) { androidx.compose.material3.Text("Play", color = it) }
                CrashReportNotice(true, { sent++ }, { discarded++ })
            }
            LaunchedEffect(Unit) { primary.requestFocus() }
        } }
        suspend fun settle(frames: Int = 100) { repeat(frames) { scene.render(frame++ * 16_666_667L).close(); delay(12) } }
        fun focusedTag() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.config?.getOrNull(SemanticsProperties.TestTag)
        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            if (!consumed) focusManager!!.moveFocus(if (key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down)
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }
        override fun close() = scene.close()
    }

    @Test fun noticeNeverTakesFocusFromThePrimaryAction() = ui {
        Fixture().use { fixture ->
            fixture.settle()
            assertEquals("primary-action", fixture.focusedTag())
        }
    }

    @Test fun noticeIsReachableFromThePrimaryActionAndFocusCanLeaveIt() = ui {
        Fixture().use { fixture ->
            fixture.settle()
            fixture.press(Key.DirectionUp); fixture.settle(10)
            assertEquals("crash-report-send", fixture.focusedTag())
            fixture.press(Key.DirectionDown); fixture.settle(10)
            assertEquals("primary-action", fixture.focusedTag())
        }
    }

    @Test fun noticeShowsOnlyOnHomeAndSettings() {
        val shown = Screen.entries.filter { showsCrashNotice(it) }
        assertEquals(listOf(Screen.Home, Screen.Settings), shown)
        assertTrue(Screen.Player !in shown)
    }
}
