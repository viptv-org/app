package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlin.math.abs
import org.junit.Rule
import org.viptv.app.components.PageBackTag
import org.viptv.app.components.PageBackGlyphTag
import org.viptv.app.connection.Profile
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.entry.ProfileEditScreen
import org.viptv.app.features.entry.ProfilePinScreen
import org.viptv.app.features.history.HistoryScreen
import org.viptv.app.features.player.PhoneFailureStyle
import org.viptv.app.features.player.PlayerFailurePage
import org.viptv.app.features.settings.AccountDevicesScreen
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PageBackControlTest {
    @get:Rule val uiThread = UiThreadRule()

    private val profile = Profile("1", "Viewer", null)
    private val card = Card(MediaIdentity("back-title", "movie", emptyList()), provider_id = "addon:1",
        title = "A Title", badges = emptyList())

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }

    private fun scene(width: Int, height: Int, pointer: Boolean, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(pointer = pointer) { Surface(Modifier.fillMaxSize(), color = Palette.background) { Box(Modifier.fillMaxSize()) { content() } } }
        }

    private fun back(scene: ImageComposeScene): SemanticsNode {
        val backs = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.TestTag) == PageBackTag }
        assertEquals(1, backs.size, "each page must have exactly one shared back control; found ${backs.size}")
        val node = backs.single()
        assertTrue(node.config.contains(SemanticsActions.OnClick), "the shared back control is clickable")
        assertTrue(node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isNotEmpty(),
            "the shared back control carries the chevron's description")
        return node
    }

    private fun glyph(scene: ImageComposeScene): SemanticsNode {
        val glyphs = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.TestTag) == PageBackGlyphTag }
        assertEquals(1, glyphs.size, "each page must have exactly one chevron glyph; found ${glyphs.size}")
        return glyphs.single()
    }

    private fun assertNoBareBackText(scene: ImageComposeScene) {
        assertFalse(nodes(scene).any { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Back" } == true
        }, "the page back must be a chevron, never a bare Back text button")
    }

    private fun phonePages(): List<Pair<String, @Composable () -> Unit>> = listOf(
        "Settings list" to { PhoneSettings(profile, 0, true, null, {}, {}, {}, back = {}) },
        "Settings sub-page" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, { _ -> }, { _, _ -> }, { _ -> }, {}, {},
            initialSection = "Playback", sectionPreferences = { _, _ -> Text("Playback preferences") }) },
        "Account devices" to { AccountDevicesScreen(null, false, null, {}, { _ -> }, {}, compact = true, back = {}) },
        "Add-ons" to { AddonSettings(true, emptyList(), emptySet(), false, null, null, { _ -> }, { _, _ -> }, { _ -> }, back = {}) },
        "Profile edit" to { ProfileEditScreen(profile, 1, false, null, compact = true, back = {},
            rename = { _, _ -> }, setPin = { _, _ -> }, setAvatar = { _, _, _ -> }, delete = { _ -> }) },
        "Profile PIN" to { ProfilePinScreen(profile, compact = true, busy = false, error = null, submit = { _ -> }, back = {}) },
        "History" to { HistoryScreen(emptyList(), false, null, { _ -> }, {}, back = {}) },
        "Continue watching" to { ContinueWatchingScreen(ContinueWatchingPage(emptyList(), loaded = true), play = { _ -> }, open = { _ -> },
            more = null, retry = {}, back = {}, home = {}) },
        "Details" to { DetailsScreen(DetailSnapshot(card, episodes = emptyList()), busy = false, error = null, back = {}, sources = { _ -> }) },
        "Player failure" to { PlayerFailurePage(PhoneFailureStyle, "This source stopped working", "The source stopped.", back = {},
            insets = WindowInsets(0)) },
    )

    @Test fun phonePagesShareOneBackControlInOneSlot() {
        var first: androidx.compose.ui.geometry.Rect? = null
        for ((name, content) in phonePages()) {
            val scene = scene(390, 844, pointer = false, content)
            try {
                frames(scene)
                val node = back(scene)
                assertNoBareBackText(scene)
                val expected = if (first == null) { first = node.boundsInRoot; node.boundsInRoot } else first
                assertTrue(abs(expected.left - node.boundsInRoot.left) <= 1f &&
                    abs(expected.top - node.boundsInRoot.top) <= 1f &&
                    abs(expected.right - node.boundsInRoot.right) <= 1f &&
                    abs(expected.bottom - node.boundsInRoot.bottom) <= 1f,
                    "$name puts the back control in a different slot: ${node.boundsInRoot} vs $expected")
                assertTrue(abs(glyph(scene).boundsInRoot.left - 16f) <= 1f,
                    "$name glyph must sit on the 16dp page gutter, was ${glyph(scene).boundsInRoot.left}")
                if (name == "Settings list") capture(scene, "phone-settings-list-390")
            } finally { scene.close() }
        }
    }

    @Test fun settingsListPinsItsBackSlotWhileScrolling() {
        val scene = scene(390, 480, pointer = false) {
            PhoneSettings(profile, 0, true, null,
                profiles = {}, addons = {}, leave = {}, back = {},
                account = {}, devices = {}, stremioImport = {}, preferences = { _ -> }, parental = {},
                information = { _ -> }, addDevice = {}, householdPin = {}, sourceIntegrations = {},
                xtreamConnections = {}, password = {}, deleteAccount = {}, profilePage = {},
                linkedServices = {}, gateways = {}, vodMatches = {}, watchOnTv = true, openWatchOnTv = {})
        }
        try {
            frames(scene)
            fun list() = nodes(scene).first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            fun title() = nodes(scene).first { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Settings" } == true }
            val startOffset = list().config[SemanticsProperties.VerticalScrollAxisRange].value()
            val slot = back(scene).boundsInRoot
            val titleSlot = title().boundsInRoot
            capture(scene, "settings-list-pinned-top-390")
            list().config[SemanticsActions.ScrollBy].action!!.invoke(0f, 2000f)
            frames(scene)
            assertTrue(list().config[SemanticsProperties.VerticalScrollAxisRange].value() > startOffset,
                "the Settings list must scroll past its pinned back slot")
            assertTrue(abs(slot.left - back(scene).boundsInRoot.left) <= 1f &&
                abs(slot.top - back(scene).boundsInRoot.top) <= 1f &&
                abs(slot.right - back(scene).boundsInRoot.right) <= 1f &&
                abs(slot.bottom - back(scene).boundsInRoot.bottom) <= 1f,
                "the Settings back control must stay put while the list scrolls: ${back(scene).boundsInRoot} vs $slot")
            assertTrue(abs(titleSlot.left - title().boundsInRoot.left) <= 1f &&
                abs(titleSlot.top - title().boundsInRoot.top) <= 1f,
                "the Settings title must stay put while the list scrolls: ${title().boundsInRoot} vs $titleSlot")
            capture(scene, "settings-list-pinned-scrolled-390")
        } finally { scene.close() }
    }

    @Test fun desktopEditAndPinShareOneChevron() {
        val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
            "Profile edit" to { ProfileEditScreen(profile, 1, false, null, compact = false, back = {},
                rename = { _, _ -> }, setPin = { _, _ -> }, setAvatar = { _, _, _ -> }, delete = { _ -> }) },
            "Profile PIN" to { ProfilePinScreen(profile, compact = false, busy = false, error = null, submit = { _ -> }, back = {}) },
        )
        var first: androidx.compose.ui.geometry.Rect? = null
        for ((name, content) in pages) {
            val scene = scene(1440, 900, pointer = true, content)
            try {
                frames(scene)
                val node = back(scene)
                assertNoBareBackText(scene)
                assertEquals("Back", node.config.getOrNull(SemanticsProperties.ContentDescription)?.single(), "$name: desktop back is the bare chevron")
                val expected = if (first == null) { first = node.boundsInRoot; node.boundsInRoot } else first
                assertTrue(abs(expected.left - node.boundsInRoot.left) <= 1f &&
                    abs(expected.top - node.boundsInRoot.top) <= 1f &&
                    abs(expected.width - node.boundsInRoot.width) <= 1f &&
                    abs(expected.height - node.boundsInRoot.height) <= 1f,
                    "$name desktop back is out of slot: ${node.boundsInRoot} vs $expected")
                assertTrue(abs(glyph(scene).boundsInRoot.left - 24f) <= 1f,
                    "$name desktop glyph must sit on its 24dp inset, was ${glyph(scene).boundsInRoot.left}")
            } finally { scene.close() }
        }
    }

    @Test fun webSettingsSubPagesKeepTheChevronAtBothWidths() {
        for (width in listOf(1440, 800)) {
            val scene = scene(width, 900, pointer = true) {
                SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                    initialSection = "Playback", sectionPreferences = { _, _ -> Text("Playback preferences") },
                    returnToPlayer = {}, browser = true)
            }
            try {
                frames(scene)
                val node = back(scene)
                assertNoBareBackText(scene)
                assertEquals("Back to player", node.config.getOrNull(SemanticsProperties.ContentDescription)?.single(),
                    "the web sub-page names its way back to the player at $width")
                assertEquals(44f, node.boundsInRoot.height, "$width: the back slot keeps a fixed height")
                val title = nodes(scene).first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.contains("Playback preferences") } == true }
                assertTrue(abs(glyph(scene).boundsInRoot.left - title.boundsInRoot.left) <= 1f,
                    "$width: the glyph must line up with the page content edge, was ${glyph(scene).boundsInRoot.left} vs title ${title.boundsInRoot.left}")
                capture(scene, "web-settings-sub-page-$width")
            } finally { scene.close() }
        }
    }

    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_BACK_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs()
            scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }
}