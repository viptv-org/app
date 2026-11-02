package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.TvSettingsScreen
import org.viptv.app.features.settings.TvSettingsSection
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ChangeConnectionDialogUiTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun theLocalModeRowOpensTheChangeConnectionDialog() {
        var leaves = 0
        val scene = ImageComposeScene(390, 2600, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                SettingsScreen(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, { leaves++ })
            } }
        }
        try {
            frames(scene)
            click(scene, "Change connection"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Change connection?" }, texts(scene).toString())
            assertTrue(nodes(scene).any { text(it) == "Your local library and progress stay on this device. You'll choose a server or local mode next." }, texts(scene).toString())
            assertFalse(nodes(scene).any { text(it) == "Leave" || text(it).startsWith("Leave local mode") }, "the dialog no longer asks about leaving local mode")
            capture(scene, "change-connection-phone")
            assertEquals(0, leaves)
            click(scene, "Change connection"); frames(scene)
            assertEquals(1, leaves)
        } finally { scene.close() }
    }

    @Test fun theTvLocalModeRowOpensTheSameDialogWithTvWords() {
        var leaves = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                TvSettingsScreen(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, { leaves++ }, null,
                    initialSection = TvSettingsSection.Account)
            }
        }
        try {
            frames(scene)
            click(scene, "Change connection"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Change connection?" }, texts(scene).toString())
            assertTrue(nodes(scene).any { text(it) == "Your local library and progress stay on this TV. You'll choose a server or local mode next." }, texts(scene).toString())
            assertTrue(nodes(scene).any { text(it) == "Cancel" })
            assertFalse(nodes(scene).any { text(it) == "Leave" || text(it).startsWith("Leave local mode") }, "the dialog no longer asks about leaving local mode")
            capture(scene, "change-connection-tv")
            assertEquals(0, leaves)
            click(scene, "Change connection"); frames(scene)
            assertEquals(1, leaves)
        } finally { scene.close() }
    }

    private fun capture(scene: ImageComposeScene, name: String) = System.getenv("VIPTV_CHANGE_CONNECTION_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs()
        scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }

    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text)
    private fun click(scene: ImageComposeScene, label: String) {
        val target = nodes(scene).lastOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) } ?: fail("no clickable '$label'")
        assertTrue(target.config[SemanticsActions.OnClick].action!!.invoke())
    }
}
