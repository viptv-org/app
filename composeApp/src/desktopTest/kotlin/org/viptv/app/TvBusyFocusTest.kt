package org.viptv.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.connection.HistoryDisplay
import org.viptv.app.connection.Profile
import org.viptv.app.connection.SourceCredentialField
import org.viptv.app.connection.SourceIntegrationSchema
import org.viptv.app.connection.SourceIntegrationSnapshot
import org.viptv.app.connection.XtreamConnection
import org.viptv.app.connection.XtreamRefresh
import org.viptv.app.connection.XtreamScopes
import org.viptv.app.features.entry.TvProfileEditScreen
import org.viptv.app.features.entry.TvProfiles
import org.viptv.app.features.history.TvHistoryScreen
import org.viptv.app.features.settings.*
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.HistoryEntry
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvBusyFocusTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend () -> Unit) = runBlocking { block() }

    private class Tv(content: @Composable () -> Unit) : AutoCloseable {
        private var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            focusManager = LocalFocusManager.current
            ViptvTheme(true) { content() }
        }
        private var frame = 0L
        suspend fun frames(count: Int = 6) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); yield() }
        suspend fun settle() { frames(8); delay(80); frames(6) }
        suspend fun press(key: Key, times: Int = 1) = repeat(times) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            val direction = when (key) {
                Key.DirectionUp -> FocusDirection.Up; Key.DirectionDown -> FocusDirection.Down
                Key.DirectionLeft -> FocusDirection.Left; Key.DirectionRight -> FocusDirection.Right
                else -> null
            }
            if (!consumed && direction != null) requireNotNull(focusManager).moveFocus(direction)
            frames(6)
        }
        suspend fun ok() {
            val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
                .first { it.config.getOrNull(SemanticsProperties.Focused) == true }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames(6)
        }
        fun focused(): String? {
            val node = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
                .firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true } ?: return null
            return node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        }
        suspend fun focus(label: String, key: Key? = null) {
            repeat(14) {
                if (focused() == label) return
                if (key != null) press(key) else { requireNotNull(focusManager).moveFocus(FocusDirection.Next); frames(6) }
            }
            assertEquals(label, focused(), "reaching $label")
        }
        override fun close() = scene.close()
    }

    private suspend fun Tv.stays(label: String, afterLabel: String = label, busy: () -> Unit) {
        focus(label)
        busy()
        settle()
        assertEquals(afterLabel, focused(), "focus stays on $label while it is disabled")
    }

    private fun connection(id: Long, name: String, running: Boolean = false) =
        XtreamConnection(id, name, XtreamScopes(true, true, true, true), true, XtreamRefresh(if (running) "running" else "succeeded", null))

    private fun card(id: String, title: String) =
        Card(identity = MediaIdentity(id, "movie", emptyList()), provider_id = "simkl", title = title, badges = emptyList())

    private val keyed = SourceIntegrationSchema("fixture", "Fixture source", "NeedsConfiguration", true,
        listOf(SourceCredentialField("key", "API key", required = true)))

    @Test fun accountRetryKeepsFocusWhileTheAccountLoads() = onEdt {
        var busy by mutableStateOf(false)
        val initial = FocusRequester()
        Tv { TvSettingsAccount(false, null, 1f, initial, FocusRequester(), {}, null, null, busy, null, {}) }.use { tv ->
            tv.settle()
            tv.stays("Retry account", "Loading account…") { busy = true }
        }
    }

    @Test fun preferencesRetryKeepsFocusWhileTheSettingsLoad() = onEdt {
        var busy by mutableStateOf(false)
        Tv { TvPreferencesFailure("Playback", "Could not load.", busy, 1f, FocusRequester(), FocusRequester(), {}) }.use { tv ->
            tv.settle()
            tv.stays("Retry settings", "Loading settings…") { busy = true }
        }
    }

    @Test fun addonInstallButtonsKeepFocusWhileTheInstallRuns() = onEdt {
        var busy by mutableStateOf(false)
        Tv { TvAddonInstallConfirm("https://addon.invalid/manifest.json", "Vynxc", false, busy, 1f, {}, {}) }.use { tv ->
            tv.settle()
            tv.stays("Install") { busy = true }
            busy = false
            tv.settle()
            assertEquals("Install", tv.focused(), "Install keeps focus once the install ends")
            tv.press(Key.Tab)
            tv.stays("Cancel") { busy = true }
        }
    }

    @Test fun xtreamActionsKeepFocusWhileTheRequestRuns() = onEdt {
        var busy by mutableStateOf(false)
        val first = FocusRequester()
        Tv {
            TvXtreamConnections(listOf(connection(1, "Living room")), null, busy, null, null, true, "managed", 1f, first, FocusRequester(),
                {}, { _, _, _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {}, {})
        }.use { tv ->
            tv.settle()
            first.requestFocus(); tv.frames()
            assertTrue(tv.focused().orEmpty().startsWith("Living room"), "the row is focused: ${tv.focused()}")
            tv.ok(); tv.settle()
            tv.stays("Sync now") { busy = true }
            busy = false
            tv.settle()
            tv.focus("Remove connection", Key.DirectionDown)
            tv.ok(); tv.settle()
            tv.stays("Remove") { busy = true }
        }
    }

    @Test fun xtreamSaveAndCancelKeepFocusWhileTheRequestRuns() = onEdt {
        var busy by mutableStateOf(false)
        val first = FocusRequester()
        Tv {
            TvXtreamConnections(listOf(connection(1, "Living room")), null, busy, null, null, true, "managed", 1f, first, FocusRequester(),
                {}, { _, _, _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {}, {})
        }.use { tv ->
            tv.settle()
            first.requestFocus(); tv.frames()
            tv.ok(); tv.settle()
            tv.focus("Edit name and content", Key.DirectionDown)
            tv.ok(); tv.settle()
            tv.focus("Cancel", Key.DirectionDown)
            tv.stays("Cancel") { busy = true }
            busy = false
            tv.settle()
            tv.press(Key.DirectionLeft)
            tv.stays("Save") { busy = true }
        }
    }

    @Test fun sourceIntegrationFormButtonsKeepFocusWhileTheSaveRuns() = onEdt {
        var busy by mutableStateOf(false)
        Tv { TvSourceIntegrationPage(keyed, busy, null, {}, {}, 1f) }.use { tv ->
            tv.settle()
            tv.focus("Save", Key.DirectionRight)
            tv.stays("Save", "") { busy = true }
        }
        busy = false
        Tv { TvSourceIntegrationPage(keyed, busy, null, {}, {}, 1f) }.use { tv ->
            tv.settle()
            tv.focus("Cancel", Key.DirectionRight)
            tv.stays("Cancel") { busy = true }
        }
    }

    @Test fun xtreamFieldKeepsFocusWhileTheRequestRuns() = onEdt {
        var busy by mutableStateOf(false)
        val first = FocusRequester()
        Tv {
            TvXtreamConnections(listOf(connection(1, "Living room")), null, busy, null, null, true, "managed", 1f, first, FocusRequester(),
                {}, { _, _, _, _, _ -> }, { _, _, _ -> }, { _, _ -> }, {}, {}, {})
        }.use { tv ->
            tv.settle()
            first.requestFocus(); tv.frames()
            tv.ok(); tv.settle()
            tv.focus("Edit name and content", Key.DirectionDown)
            tv.ok(); tv.settle()
            assertEquals("Living room", tv.focused(), "the name field is focused")
            busy = true
            tv.settle()
            assertEquals("Living room", tv.focused(), "focus stays on the field while the request runs")
        }
    }

    @Test fun sourceIntegrationListButtonsKeepFocusWhileTheRequestRuns() = onEdt {
        var busy by mutableStateOf(false)
        val first = FocusRequester()
        Tv { TvSourceIntegrationList(SourceIntegrationSnapshot(listOf(keyed)), busy, null, {}, {}, {}, 1f, first) }.use { tv ->
            tv.settle()
            first.requestFocus(); tv.frames()
            tv.stays("Configure") { busy = true }
        }
        var failed by mutableStateOf(false)
        val retry = FocusRequester()
        Tv { TvSourceIntegrationList(SourceIntegrationSnapshot(), failed, "Could not load.", {}, {}, {}, 1f, retry) }.use { tv ->
            tv.settle()
            retry.requestFocus(); tv.frames()
            tv.stays("Retry", "Retry") { failed = true }
        }
    }

    @Test fun historyClearKeepsFocusWhileTheHistoryClears() = onEdt {
        var busy by mutableStateOf(false)
        val history = listOf(HistoryDisplay(HistoryEntry(card("m1", "Show 01"), 1L), "Today", position = 30.0, duration = 100.0, resumeActive = true))
        Tv { TvHistoryScreen(history, busy, null, open = {}, clear = {}) }.use { tv ->
            tv.settle()
            tv.stays("Clear history") { busy = true }
        }
    }

    @Test fun profileAvatarButtonKeepsFocusWhileTheProfileSaves() = onEdt {
        var busy by mutableStateOf(false)
        Tv { TvProfileEditScreen(PreviewData.profile, 2, busy, null, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}) }.use { tv ->
            tv.settle()
            tv.stays("Change avatar") { busy = true }
        }
    }

    @Test fun manageProfilesKeepsFocusWhileAProfileLoads() = onEdt {
        var busy by mutableStateOf(false)
        val profiles = listOf(Profile("p1", "Alpha", null), Profile("p2", "Beta", null))
        Tv { TvProfiles(profiles, busy, null, true, false, {}, {}, {}, {}, {}) }.use { tv ->
            tv.settle()
            tv.stays("Manage profiles") { busy = true }
        }
    }
}
