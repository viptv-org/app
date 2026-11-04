package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.sources.SourcesScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SourceEmptyAddonsActionTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun page(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television, pointer = true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 30) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun label(node: SemanticsNode): String =
        (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

    private fun texts(scene: ImageComposeScene): List<String> = nodes(scene).map(::label)

    private fun click(scene: ImageComposeScene, text: String) {
        val node = nodes(scene).first {
            it.config.contains(SemanticsActions.OnClick) && (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
        }
        assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true, "$text is clickable")
        frames(scene)
    }

    private fun visible(scene: ImageComposeScene, text: String): Boolean =
        texts(scene).any { it.contains(text) }

    private val emptyCopy = "No sources available. Check your add-ons in Settings."

    @Test fun phoneSourcePickerEmptyOffersManageAddons() {
        var managed = 0
        val scene = page(390, 844) {
            SourcesScreen(title = null, sources = emptyList(), busy = false, error = null,
                choose = {}, back = {}, manageAddons = { managed++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, emptyCopy), "the empty state reads its copy — ${texts(scene)}")
            assertTrue(visible(scene, "Manage add-ons"), "the empty state offers the recovery action")
            click(scene, "Manage add-ons")
            assertEquals(1, managed, "Manage add-ons opens the add-ons settings")
        } finally { scene.close() }
    }

    @Test fun tvSourcePickerEmptySharesTheSameManageAddonsAction() {
        var managed = 0
        val scene = page(1920, 1080, television = true) {
            SourcesScreen(title = null, sources = emptyList(), busy = false, error = null,
                choose = {}, back = {}, television = true, manageAddons = { managed++ })
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "No sources yet"), "the TV empty panel is shown")
            assertTrue(visible(scene, "Manage add-ons"), "TV keeps its Manage add-ons action")
            click(scene, "Manage add-ons")
            assertEquals(1, managed, "the shared Manage add-ons action fires on TV")
        } finally { scene.close() }
    }

    @Test fun aTorrentOnlyEmptyKeepsItsDebridAdvice() {
        val scene = page(390, 844) {
            SourcesScreen(title = null, sources = emptyList(), busy = false, error = null,
                choose = {}, back = {}, torrentOnlyProviders = 1)
        }
        try {
            frames(scene)
            assertTrue(visible(scene, "provider returned torrent links"), "the torrent-only line stays")
            assertFalse(visible(scene, emptyCopy), "the generic copy does not mix with the torrent advice")
        } finally { scene.close() }
    }
}
