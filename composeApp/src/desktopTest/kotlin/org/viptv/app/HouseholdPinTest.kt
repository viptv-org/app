package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.HouseholdPinSettings
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class HouseholdPinTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun management(pin: Boolean, kids: Boolean = false) = ProfileManagement(
        listOf(ServerProfile("1", "Viewer", kids = kids, max_age = if (kids) 10 else null)),
        ProfileCapabilities(FeatureAvailability("Available"), FeatureAvailability("Available"), FeatureAvailability("Available"),
            FeatureAvailability("Available"), FeatureAvailability("Unsupported"), FeatureAvailability("Available")),
        ParentAccess(pin, unlocked = true, restricted = false))

    @Test fun unconfiguredPinSavesTheChosenDigitsAndClosesOnSuccess() {
        var busy by mutableStateOf(false); var saved by mutableStateOf(0L)
        var snapshot by mutableStateOf(management(pin = false)); var loads = 0
        val submits = mutableListOf<Pair<String, String>>()
        val scene = scene { HouseholdPinSettings(snapshot, busy, null, null, saved, { loads++ }, {}, { current, next ->
            submits += current to next; busy = true }) }
        try {
            frames(scene); assertEquals(1, loads)
            assertTrue(nodes(scene).any { text(it) == "No PIN is set." })
            click(scene, "Set PIN"); frames(scene)
            type(scene, 0, "2468"); type(scene, 1, "2468"); frames(scene)
            click(scene, "Save PIN"); frames(scene)
            assertEquals(listOf("" to "2468"), submits)
            busy = false; snapshot = management(pin = true); saved = 1; frames(scene)
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) }, "A saved PIN closes the dialog")
            assertTrue(nodes(scene).any { text(it) == "A PIN is set." })
        } finally { scene.close() }
    }

    @Test fun changingThePinKeepsTheWrongCurrentPinAndMismatchedConfirmationInline() {
        var busy by mutableStateOf(false)
        val submits = mutableListOf<Pair<String, String>>()
        val scene = scene { HouseholdPinSettings(management(pin = true), busy, null, null, 0, {}, {}, { current, next ->
            submits += current to next }) }
        try {
            frames(scene)
            click(scene, "Change PIN"); frames(scene)
            type(scene, 0, "1234"); type(scene, 1, "2468"); type(scene, 2, "1357"); frames(scene)
            assertTrue(nodes(scene).any { text(it) == "PINs do not match." })
            assertTrue(nodes(scene).last { text(it) == "Save PIN" }.config.contains(SemanticsProperties.Disabled))
            type(scene, 2, "2468"); frames(scene)
            click(scene, "Save PIN"); frames(scene)
            assertEquals(listOf("1234" to "2468"), submits)
        } finally { scene.close() }
    }

    @Test fun removalIsBlockedWhileAnyProfileKeepsKidsModeOn() {
        val scene = scene { HouseholdPinSettings(management(pin = true, kids = true), false, null, null, 0, {}, {}, { _, _ -> }) }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Turn off kids restrictions on all profiles before removing the PIN." })
            assertTrue(clickable(scene, "Remove PIN").config.contains(SemanticsProperties.Disabled))
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) }, "A blocked removal opens no dialog")
        } finally { scene.close() }
    }

    @Test fun removalSendsTheCurrentPinWithAnEmptyReplacement() {
        var busy by mutableStateOf(false); var saved by mutableStateOf(0L)
        val submits = mutableListOf<Pair<String, String>>()
        val scene = scene { HouseholdPinSettings(management(pin = true), busy, null, null, saved, {}, {}, { current, next ->
            submits += current to next; busy = true }) }
        try {
            frames(scene)
            click(scene, "Remove PIN"); frames(scene)
            type(scene, 0, "1234"); frames(scene)
            click(scene, "Remove PIN"); frames(scene)
            assertEquals(listOf("1234" to ""), submits)
        } finally { scene.close() }
    }

    @Test fun failedLoadKeepsThePageWithOneErrorLineAndARetry() {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var loads = 0
        val scene = scene { HouseholdPinSettings(null, busy, error, null, 0, { loads++; busy = true; error = null }, {}, { _, _ -> }) }
        try {
            frames(scene); assertEquals(1, loads)
            busy = false; error = "Could not load the household PIN."; frames(scene)
            assertEquals(1, nodes(scene).count { text(it) == error })
            click(scene, "Retry the household PIN"); frames(scene)
            assertEquals(2, loads)
        } finally { scene.close() }
    }

    private fun scene(content: @androidx.compose.runtime.Composable () -> Unit) =
        ImageComposeScene(390, 844, Density(1f)) { ViptvTheme { content() } }
    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun clickable(scene: ImageComposeScene, label: String) =
        assertNotNull(nodes(scene).lastOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }, "no clickable '$label'")
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(clickable(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun type(scene: ImageComposeScene, index: Int, value: String) {
        val field = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }.getOrNull(index)
            ?: fail("no field $index")
        assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
    }
}