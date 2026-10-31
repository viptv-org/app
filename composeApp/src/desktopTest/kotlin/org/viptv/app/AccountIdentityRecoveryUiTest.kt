package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.AccountIdentityScreen
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountIdentityRecoveryUiTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun failedIdentityKeepsThePageWithOneErrorLineAndARetry() {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var identity by mutableStateOf<AccountIdentity?>(null); var loads = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            AccountIdentityScreen(identity, busy, error, { loads++; busy = true; error = null })
        } }
        try {
            frames(scene); assertEquals(1, loads)
            busy = false; error = "Could not reach your server. Check the connection and try again."; frames(scene, 20)
            capture(scene, "phone-account-load-failure")
            assertEquals(1, nodes(scene).count { text(it) == error })
            click(scene, "Retry account"); frames(scene, 40)
            assertEquals(2, loads)
            assertTrue(action(scene, "Retry account").config.contains(SemanticsProperties.Disabled))
            identity = AccountIdentity("fixture", "viewer", "Viewer Name", "member"); busy = false; frames(scene, 60)
            assertTrue(nodes(scene).any { text(it) == "Name Viewer Name" })
            assertTrue(nodes(scene).any { text(it) == "Username @viewer" })
            assertTrue(nodes(scene).any { text(it) == "Role Member" })
            assertFalse(nodes(scene).any { text(it) == "Retry account" })
            identity = identity?.copy(role = "owner"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Role Owner" })
            assertFalse(nodes(scene).any { text(it) in listOf("Link Simkl", "Unlink Simkl") },
                "linked services live under the profile, not the account")
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
