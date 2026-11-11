package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.TvProfileEditAction
import org.viptv.app.features.entry.TvProfileEditDialog
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

// AND-KEYBOARD-001: Android TV uses a native numeric field, so the profile lock asks for its PIN like the household-PIN prompt, not on an app keypad.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvProfileLockDialogTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val calls = mutableListOf<String>()

    private fun Panel.calls(): List<String> = calls

    private inner class Panel(profile: Profile, width: Int = 1920, height: Int = 1080) : AutoCloseable {
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(true) {
                TvProfileEditDialog(TvProfileEditAction.Pin, profile, false, { calls += "dismiss" }, { _, _ -> },
                    { current, next -> calls += "pin:$current:$next" }, {}, pinAvailable = true, scale = width / 1920f)
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun title() = nodes().firstOrNull { text(it).isNotEmpty() && !it.config.contains(SemanticsActions.OnClick) }
        fun focusedText() = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::text).orEmpty()
        fun click(label: String) {
            val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$label' in ${nodes().map(::text).filter(String::isNotBlank)}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames()
        }
        fun type(value: String) {
            val field = nodes().firstOrNull { it.config.contains(SemanticsActions.SetText) } ?: fail("no text field")
            field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames()
        }
        fun fieldValue() = nodes().firstOrNull { it.config.contains(SemanticsActions.SetText) }
            ?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
        }
        fun frames() = repeat(24) { scene.render(++frame * 16_666_667L).close() }
        fun capture(name: String) {
            System.getenv("VIPTV_PROFILE_CAPTURE_DIR")?.let { path ->
                java.io.File(path).mkdirs()
                scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { java.io.File(path, "$name.png").writeBytes(it.bytes) } }
            }
        }
        override fun close() = scene.close()
    }

    private val locked = Profile("1", "Viewer", null, locked = true)

    @Test fun theLockAsksForThePinsInTvFieldsNotAnAppKeypad() {
        Panel(locked).use { panel ->
            panel.frames()
            val title = requireNotNull(panel.title())
            assertEquals("Enter the current PIN", panel.text(title))
            assertTrue(title.boundsInRoot.height >= 44f, "the title is T type (height ${title.boundsInRoot.height})")
            assertTrue(panel.focusedText().contains("Current PIN"), "the remote lands on the current PIN, was '${panel.focusedText()}'")
            assertFalse(panel.nodes().any { panel.text(it) == "Erase" || panel.text(it) == "OK" }, "no app-rendered keypad")
            panel.type("1234")
            assertEquals("••••", panel.fieldValue(), "the PIN field is masked")
            panel.click("Continue")
            assertEquals("Profile lock", panel.text(requireNotNull(panel.title())))
            assertTrue(panel.focusedText().contains("New PIN"), "the new PIN field takes focus, was '${panel.focusedText()}'")
            panel.type("5678")
            panel.click("Save")
            assertTrue("pin:1234:5678" in panel.calls(), "the lock saves the typed pins: ${panel.calls()}")
        }
    }

    @Test fun anEmptyNewPinRemovesTheLockAndBackClosesTheDialog() {
        Panel(locked).use { panel ->
            panel.frames()
            panel.type("1234")
            panel.click("Continue")
            panel.click("Remove PIN")
            assertTrue("pin:1234:" in panel.calls(), "an empty new PIN removes the lock: ${panel.calls()}")
        }
        calls.clear()
        Panel(locked).use { panel ->
            panel.frames()
            panel.key(Key.Escape)
            assertEquals(listOf("dismiss"), calls, "Back closes the dialog")
        }
    }

    @Test fun theLockRendersAt1920And1024() {
        for ((width, height) in listOf(1920 to 1080, 1024 to 768)) {
            Panel(locked, width, height).use { panel ->
                panel.frames()
                panel.capture("tv-profile-lock-current-$width")
                panel.type("1234")
                panel.click("Continue")
                panel.capture("tv-profile-lock-new-$width")
            }
        }
    }
}
