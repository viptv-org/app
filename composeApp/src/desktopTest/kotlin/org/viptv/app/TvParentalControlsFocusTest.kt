package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.ParentProfileControls
import org.viptv.app.connection.ParentTitlesState
import org.viptv.app.features.settings.ParentTitlesSettings
import org.viptv.app.features.settings.TvParentControlsSettings
import org.viptv.app.features.settings.TvSettingsScreen
import org.viptv.app.features.settings.TvSettingsSection
import org.viptv.app.preview.PreviewData
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvParentalControlsFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private val controls = mapOf(
        "1" to ParentProfileControls(parentKids(false, 12), false),
        "2" to ParentProfileControls(parentKids(true, 10, "PG-13"), false))

    @Test fun tvParentalRowsTakeFocusAndDpadReachesEveryControl() {
        val snapshot = parentFixture(kids = true, leavePin = true)
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null, notice = null,
                    install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.Parental,
                    parental = { first, sidebar, scale -> TvParentControlsSettings(snapshot, controls, false, null, null, 0,
                        {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}, first, sidebar, scale) },
                    maturity = "Up to age 10", pinAvailable = true)
                focusManager = LocalFocusManager.current
            } }
        }
        try {
            frames(scene, 0)
            assertFocus(scene, "Change parent PIN")
            listOf("Parent", "Set leave PIN", "Child", "Approved and blocked titles", "Set leave PIN").forEach { label ->
                pressDown(scene, focusManager)
                assertFocus(scene, label)
            }
            val rows = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }.map(::text)
            assertTrue(rows.any { it.contains("Approved and blocked titles") }, "the kids profile's titles row is reachable: $rows")
            assertTrue(rows.any { it.contains("Set leave PIN") }, "the leave PIN row is reachable: $rows")
            assertTrue(rows.none { it.contains("Remove parent PIN") }, "removing the PIN stays hidden while a profile is restricted: $rows")
            activate(scene, focusManager) { nodes(scene).any { text(it) == "Set Child’s leave PIN" } }
            assertTrue(nodes(scene).any { text(it) == "Set Child’s leave PIN" }, "the leave PIN row opens its dialog")
        } finally { scene.close() }
    }

    @Test fun tvRestrictionsDialogOffersTheServerRatingLimitsAndTheUnratedToggle() {
        val snapshot = parentFixture(kids = true, leavePin = true)
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null, notice = null,
                    install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.Parental,
                    parental = { first, sidebar, scale -> TvParentControlsSettings(snapshot, controls, false, null, null, 0,
                        {}, {}, { _, _ -> }, { _, _ -> }, { _, _, _ -> }, {}, first, sidebar, scale) },
                    maturity = "Up to age 10", pinAvailable = true)
                focusManager = LocalFocusManager.current
            } }
        }
        try {
            frames(scene, 0)
            repeat(3) { pressDown(scene, focusManager) }
            assertFocus(scene, "Child")
            activate(scene, focusManager) { nodes(scene).any { text(it) == "Restrictions for Child" } }
            val labels = nodes(scene).map(::text)
            assertTrue(labels.any { it == "PG-13" }, "the server's movie labels are drawn: $labels")
            assertTrue(labels.any { it == "TV-14" }, "the server's series labels are drawn")
            assertTrue(labels.any { it.contains("Allow titles with no rating") }, "the unrated toggle is drawn")
            assertTrue(nodes(scene).any { text(it).contains("PG-13") && it.config.contains(SemanticsActions.OnClick) })
            assertTrue(nodes(scene).any { text(it).contains("TV-14") && it.config.contains(SemanticsActions.OnClick) })
            assertTrue(nodes(scene).any { text(it) == "Save restrictions" && it.config.contains(SemanticsActions.OnClick) })
        } finally { scene.close() }
    }

    @Test fun tvApprovedTitlesPageTakesFocusWhenTheRowOpensIt() {
        val snapshot = parentFixture(kids = true)
        val titles = mutableStateOf<ParentTitlesState?>(null)
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null, notice = null,
                    install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.Parental,
                    parental = { first, sidebar, scale ->
                        val open = titles.value
                        if (open != null) ParentTitlesSettings(open, false, null, { titles.value = null }, {}, {}, { _, _ -> }, {}, {}, {}, {}, first)
                        else TvParentControlsSettings(snapshot, controls, false, null, null, 0, {}, {}, { _, _ -> }, { _, _ -> },
                            { _, _, _ -> }, { profile -> titles.value = ParentTitlesState(profile, "Child", loaded = true) }, first, sidebar, scale)
                    },
                    maturity = "Up to age 10", pinAvailable = true)
                focusManager = LocalFocusManager.current
            } }
        }
        try {
            frames(scene, 0)
            repeat(3) { pressDown(scene, focusManager) }
            assertFocus(scene, "Approved and blocked titles")
            activate(scene, focusManager) { titles.value != null }
            assertFocus(scene, "Back to parental controls")
            assertTrue(nodes(scene).map(::text).any { it.contains("For Child") }, "the approved-and-blocked-titles page is the one on screen")
        } finally { scene.close() }
    }

    private fun activate(scene: ImageComposeScene, focusManager: FocusManager?, opened: () -> Boolean) {
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        frames(scene, 6)
        if (opened()) return
        val action = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?.config?.getOrNull(SemanticsActions.OnClick)?.action
        assertNotNull(action, "the focused row is activatable")
        action()
        frames(scene, 6)
    }

    private fun pressDown(scene: ImageComposeScene, focusManager: FocusManager?) {
        val consumed = scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp))
        if (!consumed) focusManager?.moveFocus(FocusDirection.Down)
        frames(scene, 6)
    }

    private fun assertFocus(scene: ImageComposeScene, label: String) {
        frames(scene, 20)
        val focused = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::text)
        assertTrue(focused.any { it.contains(label) }, "focus is on $label, not $focused")
    }

    private fun frames(scene: ImageComposeScene, start: Int) = repeat(20) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
