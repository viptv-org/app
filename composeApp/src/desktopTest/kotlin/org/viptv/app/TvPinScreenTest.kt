package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
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
import org.viptv.app.features.entry.TvPinScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

// AND-KEYBOARD-001: the TV profile gate asks for its PIN in a native masked numeric field, not an app-rendered key grid.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvPinScreenTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val locked = Profile("1", "Viewer", null, locked = true)

    private class Screen(profile: Profile, leaving: Boolean = false, error: String? = null, width: Int = 1920, height: Int = 1080) : AutoCloseable {
        var pin by mutableStateOf("")
        var submitted: String? = null
        var backs = 0
        var frame = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    TvPinScreen(profile, pin, false, error, { digit -> if (pin.length < 8) pin += digit }, { pin = pin.dropLast(1) },
                        { submitted = pin }, { backs += 1 }, leaving)
                }
            }
        }

        fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun described(node: SemanticsNode) =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        fun field() = nodes().firstOrNull { it.config.contains(SemanticsActions.SetText) }
        fun type(value: String) {
            field()!!.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)); frames()
        }
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

    @Test fun theGateTakesItsPinInANativeFieldNotAKeypad() {
        Screen(locked).use { screen ->
            screen.frames()
            val field = screen.field()
            assertNotNull(field, "the gate offers a text field")
            assertTrue(field.config.getOrNull(SemanticsProperties.Focused) == true, "the field takes focus")
            assertTrue(screen.text(field) == "PIN" || screen.described(field).contains("PIN"), "the field is labelled PIN")
            assertFalse(screen.nodes().any { screen.text(it) == "Erase" || screen.text(it) == "OK" }, "no app-rendered keypad")
            for (key in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
                assertFalse(screen.nodes().any { screen.text(it) == key && it.config.contains(SemanticsActions.OnClick) }, "no '$key' key")
        }
    }

    @Test fun typingIntoTheFieldFeedsThePinState() {
        Screen(locked).use { screen ->
            screen.frames()
            screen.type("1234")
            assertEquals("1234", screen.pin)
            screen.type("12")
            assertEquals("12", screen.pin, "deleting goes through erase")
            screen.type("9876")
            assertEquals("9876", screen.pin, "replacing the value rewrites it")
            screen.type("123456789")
            assertEquals("12345678", screen.pin, "the PIN stops at 8 digits")
        }
    }

    @Test fun enterSubmitsACompletePinAndEscapeLeavesTheScreen() {
        Screen(locked).use { screen ->
            screen.frames()
            screen.type("1234")
            screen.key(Key.Enter)
            assertEquals("1234", screen.submitted, "Enter submits a 4–8 digit PIN")
            screen.key(Key.Escape)
            assertEquals(1, screen.backs, "Escape leaves the screen")
        }
        Screen(locked).use { screen ->
            screen.frames()
            screen.type("12")
            screen.key(Key.Enter)
            assertNull(screen.submitted, "a short PIN is not submitted")
        }
    }

    @Test fun theTitleNamesWhatThePinIsForAndTheErrorShows() {
        Screen(locked, leaving = true, error = "Incorrect PIN. Try again.").use { screen ->
            screen.frames()
            assertTrue(screen.nodes().any { screen.text(it).startsWith("Enter the PIN to leave Viewer’s profile") })
            assertTrue(screen.nodes().any { screen.text(it) == "Incorrect PIN. Try again." })
            screen.capture("tv-pin-leaving-1920")
        }
        Screen(locked.copy(lockKind = "parent"), width = 1024, height = 768).use { screen ->
            screen.frames()
            assertTrue(screen.nodes().any { screen.text(it) == "Enter the account parent PIN" })
            screen.capture("tv-pin-parent-1024")
        }
    }
}
