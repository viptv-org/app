package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.TvSettingsPreferences
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvSettingsGroupsFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private var focusManager: FocusManager? = null
    private var clock = 0L
    private val left = mutableListOf<String>()

    private fun scene(section: String = "Playback") = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
        focusManager = LocalFocusManager.current
        SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {},
            { left += "switch profile" }, { left += "leave" }, null,
            television = true, initialSection = section, tvPreferences = TvSettingsPreferences(), changeTvPreferences = { },
            editProfile = { left += "profile" })
    } }

    @Test fun theRailShowsTheFourGroupLabelsProfileAndSwitchProfileAndSkipsLabelsWithTheRemote() = runBlocking {
        val scene = scene()
        try {
            frames(scene)
            val nodes = nodes(scene)
            assertTrue(nodes.filter { it.config.getOrNull(SemanticsProperties.Heading) != null }.map(::text)
                .containsAll(listOf("PROFILE", "PLAYBACK", "THIS DEVICE", "ACCOUNT")), "the rail labels every group")
            val rail = nodes.filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInRoot.left < 560f }
            assertEquals(listOf("Profile", "Switch profile", "Parental controls", "Playback", "Subtitles & audio", "Display", "About",
                "Account", "Add-ons", "Source integrations"), rail.map(::text), "the rail's rows in group order")
            for (label in listOf("PROFILE", "PLAYBACK", "THIS DEVICE", "ACCOUNT")) {
                val node = nodes.first { text(it) == label }
                assertFalse(node.config.contains(SemanticsActions.OnClick), "$label is not a row")
                assertFalse(node.config.contains(SemanticsActions.RequestFocus), "$label is not a focus stop")
            }

            assertFocused(scene, "Next episode button")
            press(scene, Key.DirectionLeft)
            assertFocused(scene, "Playback")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Subtitles & audio")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Display")

            click(scene, "Profile")
            click(scene, "Switch profile")
            assertEquals(listOf("profile", "switch profile"), left)
        } finally { scene.close() }
    }

    @Test fun leftFromAPageLandsOnTheRailRowThatOpenedIt() = runBlocking {
        for (section in listOf("Playback", "Subtitles & audio", "About")) {
            val scene = scene(section)
            try {
                frames(scene)
                click(scene, section)
                press(scene, Key.DirectionRight)
                assertFalse(focused(scene)!!.boundsInRoot.left < 560f, "$section: Right leaves the rail")
                press(scene, Key.DirectionLeft)
                val now = focused(scene)?.let(::text).orEmpty()
                assertTrue(now.startsWith(section), "$section: Left lands on the rail row that opened it, was '$now'")
            } finally { scene.close() }
        }
    }

    private suspend fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(clock++ * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun focused(scene: ImageComposeScene) = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene)
        val now = focused(scene)?.let(::text).orEmpty()
        assertTrue(now.startsWith(label), "expected focus on $label, was '$now'")
    }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        focusManager!!.moveFocus(when (key) {
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        frames(scene)
    }
    private fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            ?: fail("no '$label' in ${nodes(scene).map(::text).filter(String::isNotBlank)}")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}
