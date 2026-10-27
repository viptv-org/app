package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.ParentControlsSettings
import org.viptv.app.features.settings.ParentPinDialog
import org.viptv.app.features.settings.ParentTitlesSettings
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ParentControlsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun parentPinRequiresMatchingConfirmationAndCancelNeverSubmits() {
        var submitted: Pair<String, String>? = null
        var canceled = 0
        val scene = ImageComposeScene(720, 820, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ParentPinDialog("save", true, false, null, { canceled++ }, { old, next -> submitted = old to next })
            } }
        }
        try {
            frames(scene, 0)
            val fields = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.sortedBy { it.boundsInRoot.top }
            assertEquals(3, fields.size)
            fun type(index: Int, value: String) { assertTrue(fields[index].config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))) }
            type(0, "0123"); type(1, "4567"); type(2, "4568")
            frames(scene, 20)
            assertTrue(nodes(scene).single { text(it) == "Save PIN" && it.config.contains(SemanticsActions.OnClick) }.config.contains(SemanticsProperties.Disabled))
            assertNull(submitted)
            type(2, "4567"); frames(scene, 40)
            val save = nodes(scene).single { text(it) == "Save PIN" && it.config.contains(SemanticsActions.OnClick) }
            assertFalse(save.config.contains(SemanticsProperties.Disabled))
            save.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals("0123" to "4567", submitted)
            nodes(scene).single { text(it) == "Cancel" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, canceled)
            capture(scene, "pin-form")
        } finally { scene.close() }
    }

    @Test fun compactHouseholdControlsLoadOnceAndKeepActionsWithinWidth() {
        var loads = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ParentControlsSettings(parentFixture(kids = true), false, null, null, 0, { loads++ }, {}, { _, _ -> }, { _, _ -> })
            } }
        }
        try {
            frames(scene, 0)
            assertEquals(1, loads)
            assertTrue(nodes(scene).any { text(it) == "Parental controls" })
            assertTrue(nodes(scene).any { text(it) == "Kids profile · up to age 10" })
            nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.forEach {
                assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= 390, "Action overflows compact settings")
            }
            capture(scene, "phone-parent-controls")
        } finally { scene.close() }
    }
    @Test fun titleApprovalRequiresConfirmationAndUnsupportedResultsStayDisabled() {
        var approvals = 0
        val card = Card(MediaIdentity("tt123", "movie", emptyList()), provider_id = "stremio:7", title = "Example film", badges = emptyList())
        val state = ParentTitlesState("2", "Child", searched = true, loaded = true, rows = listOf(
            ParentTitleRow("addon-7", "Example addon", "stremio", 7, items = listOf(
                ParentTitleItem(card, true), ParentTitleItem(card.copy(identity = card.identity.copy(id = "private:1"), title = "Unsupported film"), false, "Private title approval unavailable")))))
        val scene = ImageComposeScene(720, 1200, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ParentTitlesSettings(state, false, null, {}, {}, {}, { _, _ -> approvals++ }, {})
            } }
        }
        try {
            frames(scene, 0)
            val actions = nodes(scene).filter { text(it) == "Approve title" && it.config.contains(SemanticsActions.OnClick) }
            assertEquals(2, actions.size)
            assertEquals(1, actions.count { it.config.contains(SemanticsProperties.Disabled) })
            actions.single { !it.config.contains(SemanticsProperties.Disabled) }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 20)
            assertEquals(0, approvals)
            nodes(scene).single { text(it) == "Cancel" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 40); assertEquals(0, approvals)
            nodes(scene).single { text(it) == "Approve title" && it.config.contains(SemanticsActions.OnClick) && !it.config.contains(SemanticsProperties.Disabled) }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 60)
            nodes(scene).single { text(it) == "Approve" && it.config.contains(SemanticsActions.OnClick) }.config[SemanticsActions.OnClick].action!!.invoke()
            frames(scene, 80); assertEquals(1, approvals)
            capture(scene, "parent-title-approvals")
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, start: Int) = repeat(20) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun capture(scene: ImageComposeScene, name: String) {
        val path = System.getenv("VIPTV_PARENT_CAPTURE_DIR") ?: return
        File(path).mkdirs()
        scene.render(1_500_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    }
}
