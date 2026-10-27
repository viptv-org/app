package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.LinkedAccounts
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LinkedAccountRecoveryUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun nativeFailedIdentityCanRetryWithoutInventingBrowserManagement() {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var identity by mutableStateOf<AccountIdentity?>(null); var loads = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            LinkedAccounts(false, false, null, busy, error, { loads++; busy = true; error = null }, {}, {}, {},
                identity, "https://fixture.example/")
        } }
        try {
            frames(scene); assertEquals(1, loads)
            busy = false; error = "Could not reach your server. Check the connection and try again."; frames(scene, 20)
            capture(scene, "phone-account-load-failure")
            click(scene, "Retry account"); frames(scene, 40)
            assertEquals(2, loads)
            assertTrue(action(scene, "Retry account").config.contains(SemanticsProperties.Disabled))
            assertFalse(nodes(scene).any { text(it) in listOf("Link Simkl", "Unlink Simkl") })
            identity = AccountIdentity("fixture", "viewer", "Viewer", "member"); busy = false; frames(scene, 60)
            assertTrue(nodes(scene).any { text(it) == "Username · viewer" })
            assertFalse(nodes(scene).any { text(it) == "Retry account" })
            click(scene, "Refresh account"); assertEquals(3, loads)
        } finally { scene.close() }
    }

    @Test fun browserRetryReturnsAnHonestUnconfiguredStateInsteadOfALinkAction() {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var status by mutableStateOf<SimklLinkStatus?>(null); var loads = 0
        val scene = ImageComposeScene(800, 700, Density(1f)) { ViptvTheme {
            LinkedAccounts(false, true, status, busy, error, { loads++; busy = true; error = null }, {}, {}, {})
        } }
        try {
            frames(scene); busy = false; error = "The server could not complete this request."; frames(scene, 20)
            click(scene, "Retry account"); frames(scene, 40); assertEquals(2, loads)
            assertTrue(action(scene, "Retry account").config.contains(SemanticsProperties.Disabled))
            status = SimklLinkStatus(false, false); busy = false; frames(scene, 60)
            assertTrue(nodes(scene).any { text(it) == "Simkl is not configured on this server." })
            assertFalse(nodes(scene).any { text(it) in listOf("Link Simkl", "Unlink Simkl", "Retry account") })
            assertFalse(action(scene, "Refresh account").config.contains(SemanticsProperties.Disabled))
        } finally { scene.close() }
    }

    @Test fun localAccountDoesNotOfferServerRetryOrMakeAccountRequests() {
        var loads = 0; var leaves = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            LinkedAccounts(true, false, null, false, null, { loads++ }, {}, {}, { leaves++ })
        } }
        try {
            frames(scene); assertEquals(0, loads)
            assertFalse(nodes(scene).any { text(it) in listOf("Retry account", "Refresh account", "Link Simkl") })
            click(scene, "Change connection"); assertEquals(1, leaves)
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_INTEGRATION_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
