package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class StartupStorageFailureUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val error = "Your saved connection could not be read from secure storage. Choose a connection to continue."

    @Test fun desktopInitialChoiceShowsStorageFailureAndKeepsLocalActionVisible() = verify(false)
    @Test fun phoneInitialChoiceShowsStorageFailureAndKeepsLocalActionVisible() = verify(true)

    private fun verify(phone: Boolean) {
        var local = 0
        val height = if (phone) 844 else 720
        val scene = ImageComposeScene(if (phone) 390 else 1280, height, Density(1f)) { ViptvTheme {
            ConnectScreen(false, "https://fixture.example", false, error, { local++ }, { _, _, _ -> }, desktop = !phone, phone = phone)
        } }
        try {
            repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
            val nodes = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            val message = nodes.single { text(it) == error }
            assertTrue(message.boundsInRoot.top >= 0 && message.boundsInRoot.bottom <= height)
            val action = nodes.single { text(it).startsWith("Use without an account") && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(action.boundsInRoot.top >= 0 && action.boundsInRoot.bottom <= height, "Local action is outside viewport: ${action.boundsInRoot}")
            assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke()); assertEquals(1, local)
            System.getenv("VIPTV_HOME_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs(); scene.render(2_000_000_000L).use { image -> image.encodeToData()!!.use {
                    File(path, "${if (phone) "phone" else "desktop"}-startup-storage-error.png").writeBytes(it.bytes)
                } }
            }
        } finally { scene.close() }
    }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
