package org.viptv.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.connection.SERVER_ADDRESS_ERROR
import org.viptv.app.features.entry.DesktopPasswordPanel
import org.viptv.app.features.entry.MobilePasswordPanel
import org.viptv.app.features.entry.TvPasswordPanel
import org.viptv.app.features.entry.canSignInWithPassword
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ServerAddressUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun fields(scene: ImageComposeScene) = nodes(scene).filter { it.config.contains(SemanticsActions.SetText) }
    // The TV panel behind the dialog has its own Username and Password fields, so the address is the labelled one.
    private fun addressFieldOrNull(scene: ImageComposeScene) = fields(scene).firstOrNull { field ->
        field.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains("HTTPS server address") }
    }
    private fun addressField(scene: ImageComposeScene) = assertNotNull(addressFieldOrNull(scene), "no HTTPS server address field")
    private fun render(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) { content() } }
        return scene
    }
    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close() }
    private fun type(node: SemanticsNode, value: String) =
        assertTrue(node.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)), "cannot type into the field")
    private fun capture(scene: ImageComposeScene, name: String) {
        val dir = System.getenv("VIPTV_ADDRESS_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        repeat(15) { scene.render(it * 100_000_000L).close() }
        scene.render(1_600_000_000L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
    }
    private fun click(scene: ImageComposeScene, label: String) =
        nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
            .config[SemanticsActions.OnClick].action!!.invoke()

    @Test fun theTvPanelNoLongerInventsAServerAddress() {
        var connections = 0
        val scene = render(1920, 1080, television = true) {
            TvPasswordPanel("", false, null, 1f, { _, _, _ -> connections++ })
        }
        try {
            frames(scene)
            val dialog = addressField(scene)
            assertTrue(dialog.config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrEmpty(),
                "the address field opens empty, not on a placeholder host: ${dialog.config.getOrNull(SemanticsProperties.EditableText)}")
            assertFalse(nodes(scene).any { text(it).contains("your-server.example") }, "no placeholder host is drawn")
            type(dialog, "http://watch.example")
            frames(scene)
            click(scene, "Done")
            frames(scene)
            assertTrue(nodes(scene).any { text(it).contains(SERVER_ADDRESS_ERROR) }, "an invalid address reports the one error inline")
            capture(scene, "tv-address-error")
            type(addressField(scene), "https://watch.example")
            frames(scene)
            assertFalse(nodes(scene).any { text(it) == SERVER_ADDRESS_ERROR }, "correcting the address clears the error")
            click(scene, "Done")
            frames(scene)
            assertNull(addressFieldOrNull(scene), "a valid address closes the dialog")
        } finally { scene.close() }
        assertEquals(0, connections)
    }

    @Test fun theTvSignInWaitsForAValidAddress() {
        assertFalse(canSignInWithPassword("", "ada", "secret", busy = false), "no address")
        assertFalse(canSignInWithPassword("http://watch.example", "ada", "secret", busy = false), "a non-HTTPS address")
        assertFalse(canSignInWithPassword("https://watch.example/device", "ada", "secret", busy = false), "a page path")
        assertFalse(canSignInWithPassword("https://watch.example", "", "secret", busy = false), "no username")
        assertFalse(canSignInWithPassword("https://watch.example", "ada", "", busy = false), "no password")
        assertFalse(canSignInWithPassword("https://watch.example", "ada", "secret", busy = true), "a sign-in in flight")
        assertTrue(canSignInWithPassword("https://watch.example", "ada", "secret", busy = false))
    }

    @Test fun thePhonePanelReportsTheSameErrorUnderItsAddressField() {
        var connected: String? = null
        val scene = render(390, 844) { Column(Modifier.fillMaxSize()) {
            MobilePasswordPanel("https://watch.example", false, null, { origin, _, _ -> connected = origin })
        } }
        try {
            frames(scene)
            assertEquals(2, fields(scene).size, "username and password until the address field is opened")
            click(scene, "Use another server")
            frames(scene)
            assertEquals(3, fields(scene).size, "the address field opens")
            val (username, password, address) = fields(scene)
            type(username, "ada"); type(password, "secret"); type(address, "http://watch.example")
            frames(scene)
            click(scene, "Sign in")
            frames(scene)
            assertNull(connected, "an invalid address never reaches the server")
            assertTrue(nodes(scene).any { text(it).contains(SERVER_ADDRESS_ERROR) }, "the field carries the one error inline")
            capture(scene, "phone-address-error")
            type(fields(scene).last(), "https://watch.example")
            frames(scene)
            assertFalse(nodes(scene).any { text(it).contains(SERVER_ADDRESS_ERROR) }, "editing clears the error")
            click(scene, "Sign in")
            frames(scene)
            assertEquals("https://watch.example", connected, "a valid address signs in through the one validator")
        } finally { scene.close() }
    }

    @Test fun theDesktopPanelReportsTheSameErrorUnderItsAddressField() {
        var connected: String? = null
        val scene = render(1280, 800) {
            DesktopPasswordPanel("https://watch.example", false, null, {}, { origin, _, _ -> connected = origin })
        }
        try {
            frames(scene)
            click(scene, "Use another server")
            frames(scene)
            val (username, password, address) = fields(scene)
            type(username, "ada"); type(password, "secret"); type(address, "https://watch.example?a=1")
            frames(scene)
            click(scene, "Sign in")
            frames(scene)
            assertNull(connected, "a query string never reaches the server")
            assertTrue(nodes(scene).any { text(it).contains(SERVER_ADDRESS_ERROR) }, "the field carries the one error inline")
            capture(scene, "desktop-address-error")
            type(fields(scene).last(), "https://watch.example")
            frames(scene)
            click(scene, "Sign in")
            frames(scene)
            assertEquals("https://watch.example", connected)
        } finally { scene.close() }
    }

    @Test fun noEntryPanelInventsAServerAddressOrItsOwnErrorWording() {
        val root = File("src/commonMain")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertEquals(emptyList(), files.filter { it.readText().contains("your-server.example") && it.parentFile.name == "entry" }
            .map { it.name }, "none of the entry panels prefills a placeholder host")
        assertEquals(emptyList(), files.filter { it.readText().contains("Enter a valid HTTPS server address.") }.map { it.name })
        assertEquals(1, files.count { it.readText().contains("without a page path") }, "one server-address error line")
    }
}