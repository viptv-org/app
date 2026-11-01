package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.AccountDevices
import org.viptv.app.connection.AccountIdentity
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvAccountDevicesFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private val snapshot = AccountDevices(
        account = AccountIdentity("1", "viewer", "Viewer", "member"),
        devices = listOf(
            AccountDevice("tv", "Living room TV", "device", 1791982800, current = true, can_sign_out = false),
            AccountDevice("phone", "Phone", "device", 1791896400, current = false, can_sign_out = true),
        ),
        can_sign_out_all = true,
    )

    private var focusManager: FocusManager? = null
    private var frame = 0L

    private fun scene() = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
        focusManager = LocalFocusManager.current
        TvSettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, null,
            initialSection = TvSettingsSection.Account, account = TvAccountInfo("Viewer", "viewer", "member"),
            accountRequested = {},
            tvDevices = { first, sidebar, scale, back ->
                TvAccountDevices(snapshot, false, null, {}, scale, first, sidebar, back)
            })
    } }

    @Test fun remoteWalksIntoTheReadOnlyDeviceListAndNoSignOutIsActivatable() = runBlocking {
        val scene = scene()
        try {
            settle(scene)
            assertFocused(scene, "Devices & sessions")
            activate(scene)
            assertFocused(scene, "Living room TV")
            assertTrue(text(requireNotNull(focused(scene))).contains("This device"), "the current device reads This device")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Phone")
            assertTrue(text(requireNotNull(focused(scene))).contains("Sign out"), "an other device keeps its sign-out slot")
            assertTrue(nodes(scene).none { text(it).contains("Sign out") && it.config.contains(SemanticsActions.OnClick) },
                "no row offers a working sign-out")
            assertTrue(nodes(scene).any { text(it).contains(tvAccountManagedElsewhere) }, "the reason for read-only is shown")
            assertTrue(nodes(scene).any { text(it) == "Sign out of all devices $tvAccountManagedElsewhere" },
                "sign out of all devices is disabled with its reason")
            capture(scene)
        } finally { scene.close() }
    }

    private suspend fun frames(scene: ImageComposeScene) = repeat(20) {
        scene.render((frame + it) * 16_666_667L).close(); delay(4)
    }
    private suspend fun settle(scene: ImageComposeScene) { frames(scene); frame += 20 }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun focused(scene: ImageComposeScene) = nodes(scene).singleOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }

    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene); frame += 20
        val now = focused(scene)?.let(::text).orEmpty()
        assertTrue(now.contains(label), "expected focus on $label, was '$now'")
    }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        frames(scene); frame += 20
    }
    private suspend fun activate(scene: ImageComposeScene) {
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        frames(scene); frame += 20
        if (focused(scene)?.let(::text)?.contains("Living room TV") == true) return
        val action = focused(scene)?.config?.getOrNull(SemanticsActions.OnClick)?.action
        assertNotNull(action, "the Devices & sessions row is activatable")
        action(); frames(scene); frame += 20
    }
    private fun capture(scene: ImageComposeScene) {
        val directory = System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(frame * 16_666_667L).use { image ->
            image.encodeToData()!!.use { File(directory, "account-devices-tv.png").writeBytes(it.bytes) }
        }
    }
}