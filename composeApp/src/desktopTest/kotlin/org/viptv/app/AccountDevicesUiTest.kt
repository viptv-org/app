package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.viptv.app.theme.Palette
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.*
import org.viptv.app.connection.AccountDevices
import org.viptv.app.features.settings.AccountDevicesScreen
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountDevicesUiTest {
    private val snapshot = Json.decodeFromString<AccountDevices>(accountFixture)

    @Test fun devicesLoadOnceAndCurrentBrowserHasNoPerDeviceSignOut() {
        var loads = 0
        val scene = ImageComposeScene(760, 700, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { AccountDevicesScreen(snapshot, false, null, { loads++ }, {}, {}) } }
        }
        try {
            val probe = Probe(scene); probe.frames(16)
            assertEquals(1, loads)
            assertEquals(1, probe.nodes().count { probe.text(it) == "Sign out" && it.config.contains(SemanticsActions.OnClick) })
            assertTrue(probe.nodes().any { probe.text(it) == "This device" })
            assertTrue(probe.nodes().any { probe.text(it).startsWith("Signed in ") })
            probe.capture("desktop")
        } finally { scene.close() }
    }

    @Test fun compactDeviceListKeepsActionsInsideTheViewport() {
        val scene = ImageComposeScene(390, 720, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) { AccountDevicesScreen(snapshot, false, null, {}, {}, {}, compact = true) } }
        }
        try {
            val probe = Probe(scene); probe.frames(16)
            val controls = probe.nodes().filter { it.config.contains(SemanticsActions.OnClick) }
            assertTrue(controls.isNotEmpty())
            controls.forEach { assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= 390, "Action leaves the phone viewport") }
            probe.capture("phone")
        } finally { scene.close() }
    }

    @Test fun signOutRequiresConfirmationAndCancelDoesNotPublishAMutation() {
        val signedOut = mutableListOf<String>()
        var all = 0
        val scene = ImageComposeScene(760, 700, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                AccountDevicesScreen(snapshot, false, null, {}, signedOut::add, { all++ })
            } }
        }
        try {
            val probe = Probe(scene); probe.frames(16)
            probe.click("Sign out")
            assertTrue(signedOut.isEmpty())
            probe.click("Cancel")
            assertTrue(signedOut.isEmpty()); assertEquals(0, all)
            probe.click("Sign out of all devices")
            assertEquals(0, all)
            probe.capture("confirm-all")
            probe.click("Sign out of all devices", repeats = 2)
            assertEquals(1, all, "Repeated confirmation must publish once")
        } finally { scene.close() }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = if (node.config.contains(SemanticsProperties.Text)) node.config[SemanticsProperties.Text].joinToString(" ") { it.text } else ""
        fun click(label: String, repeats: Int = 1) {
            val node = nodes().last { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            val at = node.boundsInRoot.center
            repeat(repeats) {
                scene.sendPointerEvent(PointerEventType.Press,at)
                scene.sendPointerEvent(PointerEventType.Release,at)
            }
            frames(12)
        }
        fun capture(variant: String) {
            val directory = System.getenv("VIPTV_ACCOUNT_CAPTURE_DIR") ?: return
            File(directory).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { File(directory,"account-devices-$variant.png").writeBytes(it.bytes) }
            }
        }
    }
}
