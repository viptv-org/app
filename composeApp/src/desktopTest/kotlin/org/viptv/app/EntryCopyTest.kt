package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.connection.Profile
import org.viptv.app.connection.SERVER_ADDRESS_ERROR
import org.viptv.app.connection.validatedRemoteOrigin
import org.viptv.app.features.entry.ProfilesScreen
import org.viptv.app.features.entry.TvPairingSession
import org.viptv.app.features.entry.TvPairingSignIn
import org.viptv.app.features.entry.TvWelcome
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EntryCopyTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun texts(scene: ImageComposeScene) = nodes(scene).map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    }
    private fun described(scene: ImageComposeScene) = nodes(scene).map { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    }
    private fun shows(scene: ImageComposeScene, text: String) = texts(scene).any { it.contains(text) }
    private fun rendered(scene: ImageComposeScene) = texts(scene).any { it.isNotBlank() }
    private fun capture(scene: ImageComposeScene, name: String) {
        val dir = System.getenv("VIPTV_ENTRY_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        scene.render(120_000_000L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
    }

    private fun render(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        repeat(20) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private val profiles = listOf(Profile("p1", "Ada", null, locked = true), Profile("p2", "Ben", null))

    @Test fun theServerAddressErrorIsCopyMdsOneLine() {
        assertEquals("Enter an HTTPS server address, without a page path.", SERVER_ADDRESS_ERROR)
        for (raw in listOf("http://watch.example", "https://watch.example/page", "https://watch.example?a=1",
            "https://watch.example#section", "https://user:pass@watch.example", "watch.example", "   ")) {
            val failure = assertFailsWith<IllegalArgumentException>(raw) { validatedRemoteOrigin(raw) }
            assertEquals(SERVER_ADDRESS_ERROR, failure.message, raw)
        }
        assertEquals("https://watch.example", validatedRemoteOrigin("https://watch.example/"))
    }

    @Test fun everyProfilePickerLayoutUsesCopyMdsWords() {
        val layouts = listOf(
            "phone" to render(390, 844) { ProfilesScreen(profiles, false, null, true, false, compact = true, desktop = false,
                select = {}, edit = {}, manage = {}, done = {}, create = {}) },
            "desktop" to render(1280, 800) { ProfilesScreen(profiles, false, null, true, false, compact = false, desktop = true,
                select = {}, edit = {}, manage = {}, done = {}, create = {}) },
            "web" to render(1280, 800) { ProfilesScreen(profiles, false, null, true, false, compact = false, desktop = false,
                select = {}, edit = {}, manage = {}, done = {}, create = {}) },
            "TV" to render(1920, 1080, television = true) { ProfilesScreen(profiles, false, null, true, false, compact = false,
                desktop = false, select = {}, edit = {}, manage = {}, done = {}, create = {}, television = true) },
        )
        for ((name, scene) in layouts) {
            try {
                assertTrue(rendered(scene), "$name drew nothing")
                assertTrue(shows(scene, "Who's watching?"), "$name: the picker heading is copy.md's")
                assertFalse(shows(scene, "Who’s watching?"), "$name: copy.md writes a straight apostrophe")
                assertTrue(shows(scene, "Add a profile"), "$name: the add tile is copy.md's")
                assertTrue(shows(scene, "Manage profiles"), "$name: the manage action is copy.md's")
                assertFalse(shows(scene, "Locked"), "$name: the lock is a glyph, not a word")
                assertTrue(described(scene).any { it.contains("Locked") }, "$name: the glyph keeps its accessible name")
                capture(scene, "profiles-$name")
            } finally { scene.close() }
        }
    }

    @Test fun theTvLocalModeLineIsCopyMds() {
        val scene = render(1920, 1080, television = true) { TvWelcome(1f, signIn = {}, local = {}) }
        try {
            assertTrue(shows(scene, "Your addons and playback stay on this device. No account, profiles, or sync."),
                "TV: the local-mode line is copy.md's")
            assertFalse(shows(scene, "Without an account, everything stays on this TV"), "TV: the old line is gone")
            capture(scene, "tv-welcome-local")
        } finally { scene.close() }
    }

    @Test fun anExpiredTvPairingCodeOffersTryAgain() {
        var refreshes = 0
        val expired = render(1920, 1080, television = true) {
            TvPairingSignIn(1f, session = null, refresh = { refreshes++ }, error = "This code expired.")
        }
        try {
            assertTrue(shows(expired, "This code expired."), "the expired code says copy.md's line")
            assertTrue(shows(expired, "Try again"), "TV pairing offers Try again")
            assertFalse(shows(expired, "New code"), "not a new code while the last one expired")
            capture(expired, "tv-pairing-expired")
        } finally { expired.close() }
        val live = render(1920, 1080, television = true) {
            TvPairingSignIn(1f, TvPairingSession("https://example.invalid/device?code=AB12CD34EF", "example.invalid/device",
                "AB12CD34EF", "14:52"), refresh = { refreshes++ })
        }
        try {
            assertTrue(shows(live, "New code"), "a live code still offers a new one")
            assertFalse(shows(live, "Try again"), "no retry while the code is live")
            capture(live, "tv-pairing-live")
        } finally { live.close() }
    }

    @Test fun theRetiredEntryWordsAreGoneFromEverySource() {
        val root = File("src/commonMain")
        val entries = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for (retired in listOf("\"Add profile\"", "Who’s watching?", "Without an account, everything stays on this TV",
            "Enter a valid HTTPS server address.", "This sign-in code expired.", "Enter an HTTPS server address.\"")) {
            assertEquals(emptyList(), entries.filter { it.readText().contains(retired) }.map { it.name }, retired)
        }
        assertEquals(1, entries.count { it.readText().contains("without a page path") }, "one server-address error line")
    }
}
