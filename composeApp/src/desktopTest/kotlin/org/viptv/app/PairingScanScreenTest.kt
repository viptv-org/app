package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Rule
import org.viptv.app.features.settings.PairingScanScreen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PairingScanScreenTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
    private fun click(scene: ImageComposeScene, text: String) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        .first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true && node.config.contains(SemanticsActions.OnClick) }
        .config[SemanticsActions.OnClick].action!!.invoke()

    @Test fun theScanScreenExplainsTheCodeAndFallsBackToTyping() {
        var dismissed = 0
        val scene = ImageComposeScene(822, 1720, Density(2f)) {
            ViptvTheme { PairingScanScreen(preview = { preview -> Box(preview.background(Color(0xFF17171A))) }, allowed = true, dismiss = { dismissed++ }) }
        }
        try {
            repeat(12) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue("Scan the code on your TV" in texts(scene), texts(scene).toString())
            assertTrue("Point your camera at the code the TV shows." in texts(scene))
            click(scene, "Enter the code instead")
            assertEquals(1, dismissed)
            System.getenv("VIPTV_ADD_DEVICE_CAPTURE_DIR")?.let { directory ->
                File(directory).mkdirs()
                scene.render(40 * 16_666_667L).use { image -> image.encodeToData()!!.use { File(directory, "pairing-scan-phone.png").writeBytes(it.bytes) } }
            }
        } finally { scene.close() }
    }

    @Test fun aDeniedPermissionKeepsTheTypingFallbackAndSaysWhy() {
        val scene = ImageComposeScene(822, 1720, Density(2f)) {
            ViptvTheme { PairingScanScreen(preview = { _ -> }, allowed = false, dismiss = {}) }
        }
        try {
            repeat(12) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue("Enter the code on your TV instead." in texts(scene), texts(scene).toString())
            assertTrue("VIPTV needs the camera to read the code. Allow it in Settings, or type the code instead." in texts(scene))
            assertTrue("Enter the code instead" in texts(scene))
        } finally { scene.close() }
    }
}
