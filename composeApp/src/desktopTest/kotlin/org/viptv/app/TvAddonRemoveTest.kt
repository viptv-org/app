@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.features.settings.TvAddonConfigureDialog
import org.viptv.app.features.settings.TvDialogPanelTag
import org.viptv.app.features.settings.TvAddonManageDialog
import org.viptv.app.features.settings.TvAddonRemoveDialog
import org.viptv.app.state.AddonConfigureRequest
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Capability
import org.viptv.engine.wire.IntegrationConnection
import kotlin.test.*

class TvAddonRemoveTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val addon = IntegrationConnection("addon:7", "stremio", "1", null, "Torrentio", true, 1, listOf(Capability.DISCOVERY))

    private class Scene(width: Int = 1920, height: Int = 1080, density: Float = 1f, content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(density)) {
            ViptvTheme(television = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        private fun label(node: SemanticsNode): String =
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ").trim()
        fun all(name: String) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
            .firstOrNull { label(it) == name }
        fun clickable(name: String) = all(name)?.takeIf { it.config.contains(SemanticsActions.OnClick) } ?: error("no clickable $name")
        fun tagged(tag: String) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
            .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun capture(dir: String, name: String) {
            java.io.File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image ->
                image.encodeToData()!!.use { java.io.File(dir, "$name.png").writeBytes(it.bytes) }
            }
        }
        override fun close() = scene.close()
    }

    @Test fun theManagePanelOffersRemoveAddon(): Unit = runBlocking {
        var removed = 0
        Scene { TvAddonManageDialog(addon, true, configurable = true, busy = false, scale = 1f, toggle = {}, reconfigure = {}, remove = { removed++ }, dismiss = {}) }.use { scene ->
            scene.frames()
            assertTrue(scene.clickable("Remove add-on").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, removed, "Remove addon leaves the panel for the confirmation")
        }
    }

    @Test fun removingAsksFirstAndCancelKeepsTheAddOn(): Unit = runBlocking {
        var confirmed = 0
        var dismissed = 0
        Scene { TvAddonRemoveDialog(addon, local = false, busy = false, pending = false, scale = 1f, confirm = { confirmed++ }, dismiss = { dismissed++ }) }.use { scene ->
            scene.frames()
            assertTrue(scene.clickable("Cancel").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, dismissed)
            assertEquals(0, confirmed, "cancel removes nothing")
        }
    }

    @Test fun confirmingRemovesTheAddOn(): Unit = runBlocking {
        var confirmed = 0
        Scene { TvAddonRemoveDialog(addon, local = false, busy = false, pending = false, scale = 1f, confirm = { confirmed++ }, dismiss = {}) }.use { scene ->
            scene.frames()
            assertTrue(scene.clickable("Remove").config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, confirmed)
        }
    }

    @Test fun everyManageActionStaysInsideThePanel(): Unit = runBlocking {
        for (width in listOf(1920, 1280)) {
            val scale = width / 1920f
            val height = (1080 * scale).toInt()
            Scene(width, height, scale) { TvAddonManageDialog(addon, true, configurable = true, busy = false, scale = scale, toggle = {}, reconfigure = {}, remove = {}, dismiss = {}) }.use { scene ->
                scene.frames(20)
                val panel = scene.tagged(TvDialogPanelTag) ?: error("no panel at $width")
                for (action in listOf("Disable", "Reconfigure", "Remove add-on", "Cancel")) {
                    val node = scene.all(action)
                    assertNotNull(node, "$action missing at $width")
                    val bounds = node.boundsInRoot
                    assertTrue(bounds.left >= panel.boundsInRoot.left && bounds.right <= panel.boundsInRoot.right,
                        "$action must stay inside the panel at $width: action $bounds panel ${panel.boundsInRoot}")
                    assertTrue(bounds.bottom <= panel.boundsInRoot.bottom && bounds.top >= panel.boundsInRoot.top, "$action stays inside vertically at $width")
                    assertTrue(bounds.height <= 74 * scale, "$action is one line at $width (${bounds.height})")
                }
            }
        }
    }

    @Test fun rendersForEvidence(): Unit = runBlocking {
        val dir = System.getenv("VIPTV_TV_REMOVE_CAPTURE_DIR") ?: return@runBlocking
        Scene { TvAddonManageDialog(addon, true, configurable = true, busy = false, scale = 1f, toggle = {}, reconfigure = {}, remove = {}, dismiss = {}) }
            .use { it.frames(20); it.capture(dir, "manage-tv-1920") }
        val small = 1280f / 1920f
        Scene(1280, 720, small) { TvAddonManageDialog(addon, true, configurable = true, busy = false, scale = small, toggle = {}, reconfigure = {}, remove = {}, dismiss = {}) }
            .use { it.frames(20); it.capture(dir, "manage-tv-1280") }
        Scene(1280, 720, small) { TvAddonRemoveDialog(addon, local = false, busy = false, pending = false, scale = small, confirm = {}, dismiss = {}) }
            .use { it.frames(20); it.capture(dir, "remove-tv-1280") }
        Scene { TvAddonRemoveDialog(addon, local = false, busy = false, pending = false, scale = 1f, confirm = {}, dismiss = {}) }
            .use { it.frames(20); it.capture(dir, "remove-tv-1920") }
        Scene { TvAddonConfigureDialog(AddonConfigureRequest("addon:7", "Torrentio", "https://watch.example/api/addons/configure/ticket-1", 300), 1f, {}, {}) }
            .use { it.frames(20); it.capture(dir, "qr-tv-1920") }
    }
}
