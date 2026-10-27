package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

/**
 * app#55 / app#96: a fresh web visitor types a username and password and presses Enter, as on any
 * sign-in page. Enter used to do nothing, so no login request was ever made and the visitor was stuck.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class BrowserSignInSubmitTest {
    @get:Rule val uiThread = UiThreadRule()
    private var frame = 0L
    private val origin = "https://watch.example"
    private val logins = mutableListOf<Triple<String, String, String>>()
    private lateinit var scene: ImageComposeScene

    /** Built inside the rule, on the Swing event thread (app#27). */
    @BeforeTest fun open() {
        scene = ImageComposeScene(1280, 800, Density(1f)) { ViptvTheme {
            ConnectScreen(browser = true, origin = origin, busy = false, error = null, local = null,
                connect = { server, username, password -> logins += Triple(server, username, password) },
                register = { _, _, _ -> }, recover = { _, _, _ -> })
        } }
    }

    private fun frames(count: Int = 16) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun field(label: String) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.single { node ->
        node.config.contains(SemanticsActions.SetText) && flatten(node).any { child ->
            child.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == label } }
    }
    private fun focused(label: String) = field(label).config.getOrNull(SemanticsProperties.Focused) == true
    private fun type(label: String, value: String) {
        val node = field(label)
        if (!focused(label)) assertTrue(node.config[SemanticsActions.RequestFocus].action!!.invoke())
        frames(4)
        assertTrue(field(label).config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
        frames(4)
    }
    private fun enter() {
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        frames()
    }

    @AfterTest fun close() = scene.close()

    @Test fun enterInTheUsernameMovesToThePasswordAndEnterThereSignsIn() {
        frames()
        type("Username", "visitor")
        enter()
        assertTrue(logins.isEmpty(), "Enter in Username must not sign in without a password")
        assertTrue(focused("Password"), "Enter in Username moves to Password")
        type("Password", "secret")
        enter()
        assertEquals(listOf(Triple(origin, "visitor", "secret")), logins, "Enter in Password signs in with what was typed")
    }

    @Test fun enterDoesNotSignInWhileAFieldIsEmpty() {
        frames()
        type("Password", "secret")
        enter()
        assertTrue(logins.isEmpty(), "no username yet: Enter must not post a login")
    }
}
