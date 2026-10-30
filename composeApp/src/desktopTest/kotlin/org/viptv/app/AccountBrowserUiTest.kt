package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.LocalFailure
import org.viptv.app.features.settings.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class AccountBrowserUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun televisionWithoutBrowserKeepsValidatedDashboardAddressVisible() {
        var attempts = 0
        val address = "https://example.test:18457/"
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvSettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, null,
                    initialSection = TvSettingsSection.Account, account = TvAccountInfo("Fixture", "fixture", "member"),
                    accountDashboard = { attempts++; throw LocalFailure("No browser") }, dashboardAddress = address)
            } }
        }
        try {
            frames(scene, 0)
            nodes(scene).single { text(it).startsWith("Devices & sessions") && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 20)
            assertEquals(1, attempts)
            assertTrue(nodes(scene).any { text(it) == address })
            assertTrue(nodes(scene).any { text(it) == "Open this address in a browser on your phone or computer." })
            System.getenv("VIPTV_ACCOUNT_BROWSER_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "tv-browser-fallback.png").writeBytes(it.bytes) } }
            }
            nodes(scene).single { text(it) == "Close" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 40)
            assertFalse(nodes(scene).any { text(it) == address })
        } finally { scene.close() }
    }
    private fun frames(scene: ImageComposeScene, offset: Int) = repeat(20) { scene.render((offset + it) * 16_666_667L).close(); Thread.sleep(3) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}
