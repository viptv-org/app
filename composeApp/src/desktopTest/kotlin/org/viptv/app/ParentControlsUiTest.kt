package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.KidsPolicyDialog
import org.viptv.app.features.settings.LeavePinDialog
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
                ParentControlsSettings(parentFixture(kids = true, leavePin = true), mapOf(
                    "2" to ParentProfileControls(parentKids(true, 10, "PG-13", "TV-14"), false)), false, null, null, 0,
                    { loads++ }, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {})
            } }
        }
        try {
            frames(scene, 0)
            assertEquals(1, loads)
            assertTrue(nodes(scene).any { text(it) == "Parental controls" })
            assertTrue(nodes(scene).any { text(it) == "Kids profile · movies PG-13 · series TV-14" })
            assertTrue(nodes(scene).any { text(it) == "No leave PIN" })
            assertTrue(nodes(scene).any { text(it) == "Set leave PIN" })
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
                ParentTitlesSettings(state, false, null, {}, {}, {}, { _, _ -> approvals++ }, {}, {}, {}, {})
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

    @Test fun restrictionsDialogSavesRatingLimitsAndTheUnratedToggle() {
        var saved: KidsPolicy? = null
        val profile = parentFixture(kids = true).profiles.single { it.id == "2" }
        val scene = ImageComposeScene(720, 1200, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                KidsPolicyDialog(profile, parentKids(true, 10), true, false, null, {}, { saved = it })
            } }
        }
        try {
            frames(scene, 0)
            listOf("PG-13", "TV-14").forEach { label ->
                nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                    .config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            }
            val switches = nodes(scene).filter { it.config.contains(SemanticsProperties.ToggleableState) && it.config.contains(SemanticsActions.OnClick) }
            assertEquals(2, switches.size)
            switches.last().config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 40)
            nodes(scene).single { text(it) == "Save restrictions" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(KidsPolicy(true, 10, "PG-13", "TV-14", true), saved)
            capture(scene, "restrictions-ratings")
        } finally { scene.close() }
    }

    @Test fun leavePinDialogSetsChangesAndRemovesTheProfilePin() {
        var submitted: Pair<String, String>? = null
        val scene = ImageComposeScene(720, 820, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                LeavePinDialog("Child", true, false, null, {}, { current, next -> submitted = current to next })
            } }
        }
        try {
            frames(scene, 0)
            val fields = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.sortedBy { it.boundsInRoot.top }
            assertEquals(3, fields.size)
            fun type(index: Int, value: String) { assertTrue(fields[index].config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value))) }
            type(0, "1234"); type(1, "5678"); type(2, "5678")
            frames(scene, 20)
            nodes(scene).single { text(it) == "Save PIN" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals("1234" to "5678", submitted)
            nodes(scene).single { text(it) == "Remove PIN" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals("1234" to "", submitted)
            capture(scene, "profile-leave-pin")
        } finally { scene.close() }
    }

    @Test fun blockedTitlesSitNextToApprovalsAndAskBeforeBlocking() {
        var blocks = 0
        var unblocks = 0
        val card = Card(MediaIdentity("tt123", "movie", emptyList()), provider_id = "stremio:7", title = "Example film", badges = emptyList())
        val state = ParentTitlesState("2", "Child", searched = true, loaded = true, blockedLoaded = true,
            rows = listOf(ParentTitleRow("addon-7", "Example addon", "stremio", 7, items = listOf(ParentTitleItem(card, true)))),
            blocked = listOf(BlockedTitle("tt404", "series", "Blocked show")))
        val scene = ImageComposeScene(720, 1600, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                ParentTitlesSettings(state, false, null, {}, {}, {}, { _, _ -> }, {}, { blocks++ }, { unblocks++ }, {})
            } }
        }
        try {
            frames(scene, 0)
            nodes(scene).single { text(it) == "Block title" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 20)
            nodes(scene).single { text(it) == "Block" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 40)
            assertEquals(1, blocks)
            nodes(scene).single { text(it) == "Unblock title" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 60)
            nodes(scene).single { text(it) == "Unblock" && it.config.contains(SemanticsActions.OnClick) }
                .config[SemanticsActions.OnClick].action!!.invoke(); frames(scene, 80)
            assertEquals(1, unblocks)
            capture(scene, "blocked-titles")
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
