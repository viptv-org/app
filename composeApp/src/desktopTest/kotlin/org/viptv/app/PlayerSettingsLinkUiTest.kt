package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.connection.*
import org.viptv.app.features.player.*
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.player.*
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

/**
 * app#44: the Audio & subtitles menu ends with a link to Settings > Subtitles & audio on
 * phone, desktop/web and TV, and Back from that visit returns to the paused player by
 * every input. Set VIPTV_PLAYER_SETTINGS_CAPTURE_DIR to write the screenshots.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PlayerSettingsLinkUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val tracks = PlaybackTracks(
        audio = listOf(PlaybackTrack(1, "English · 5.1", true, "en"), PlaybackTrack(2, "Japanese · Stereo", false, "ja")),
        subtitles = listOf(PlaybackTrack(3, "English", true, "en"), PlaybackTrack(4, "English (SDH)", false, "en"), PlaybackTrack(5, "Spanish", false, "es")))

    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private var frame = 0L
    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
    private suspend fun settle(scene: ImageComposeScene) = repeat(4) { repeat(8) { scene.render(frame++ * 16_666_667L).close() }; delay(10) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun label(node: SemanticsNode) = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    private fun clickable(scene: ImageComposeScene, value: String) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
        .firstOrNull { node -> label(node) == value || flatten(node).any { label(it) == value } }
    private fun click(scene: ImageComposeScene, value: String) {
        val node = assertNotNull(clickable(scene, value), "No clickable \"$value\"")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private fun shows(scene: ImageComposeScene, value: String) = nodes(scene).any { label(it) == value }
    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_PLAYER_SETTINGS_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs(); scene.render(3_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }

    /** The settings row is the menu's last row: below every track and size choice. */
    private fun assertLastRow(scene: ImageComposeScene, choices: List<String>) {
        val link = assertNotNull(clickable(scene, PLAYER_SETTINGS_LINK), "The menu has no settings row").boundsInRoot
        for (choice in choices) {
            val row = assertNotNull(clickable(scene, choice), "No \"$choice\" row").boundsInRoot
            assertTrue(link.top >= row.bottom, "The settings row must follow \"$choice\"")
        }
    }

    private fun playerMenu(phone: Boolean, width: Int, height: Int, name: String, open: String) {
        var opened = 0
        val facts = MutableSharedFlow<PlayerFact>(replay = 1)
        facts.tryEmit(PlayerFact.Tracks(tracks))
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, title = org.viptv.engine.wire.Card(org.viptv.engine.wire.MediaIdentity("m", "movie", emptyList()),
                provider_id = "metadata", title = "Movie", badges = emptyList()), preview = PlayerFact.Progress(842_000, 3_132_000),
                phone = phone, externalFacts = facts, openSettings = { opened++ })
        } }
        try {
            frames(scene); click(scene, open); frames(scene, 20)
            assertTrue(shows(scene, "Audio & subtitles"))
            assertLastRow(scene, listOf("English · 5.1", "Spanish"))
            capture(scene, name)
            click(scene, PLAYER_SETTINGS_LINK)
            assertEquals(1, opened)
        } finally { scene.close() }
    }

    @Test fun thePhoneMenuEndsWithTheSettingsLink() = playerMenu(phone = true, 844, 390, "phone-audio-subtitles-menu", "Audio & subtitles")

    @Test fun theDesktopAndWebMenuEndsWithTheSettingsLink() = playerMenu(phone = false, 1280, 720, "desktop-audio-subtitles-menu", "Audio and subtitles")

    @Test fun theMenuHasNoSettingsRowWithoutAController() {
        val facts = MutableSharedFlow<PlayerFact>(replay = 1)
        facts.tryEmit(PlayerFact.Tracks(tracks))
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, phone = true, externalFacts = facts)
        } }
        try {
            frames(scene); click(scene, "Audio & subtitles"); frames(scene, 20)
            assertNull(clickable(scene, PLAYER_SETTINGS_LINK), "Previews without a controller have nowhere to link to")
        } finally { scene.close() }
    }

    @Test fun theTvDrawerEndsWithTheSettingsLinkReachedByDown() = onEdt {
        var opened = 0
        var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(television = true) {
            focusManager = LocalFocusManager.current
            TvPlayerScreen(null, true, null, {}, {}, {}, null, TvPlaybackExtras(), null, null, null, {}, scene = TvPlayerScene.Tracks,
                initialFacts = listOf(PlayerFact.Tracks(tracks)), openSettings = { opened++ })
        } }
        try {
            settle(scene)
            assertLastRow(scene, listOf("English · 5.1", "Spanish", "Extra large"))
            fun merged() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
            fun focused() = merged().firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }?.let(::label)
            // D-pad Down from the last row of the subtitle list and of the size list reaches the settings row.
            for (last in listOf("Spanish", "Extra large")) {
                val row = merged().first { label(it) == last && it.config.contains(SemanticsActions.RequestFocus) }
                assertTrue(row.config[SemanticsActions.RequestFocus].action!!.invoke()); settle(scene)
                assertEquals(last, focused())
                if (!scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown))) requireNotNull(focusManager).moveFocus(FocusDirection.Down)
                scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp)); settle(scene)
                assertEquals(PLAYER_SETTINGS_LINK, focused(), "Down from \"$last\" must reach the settings row")
            }
            capture(scene, "tv-audio-subtitles-drawer")
            click(scene, PLAYER_SETTINGS_LINK)
            assertEquals(1, opened)
        } finally { scene.close() }
    }

    /** Settings rendered from the controller as App does, during a visit from the player. */
    private class Visit(width: Int, height: Int, browser: Boolean = false, television: Boolean = false, desktop: Boolean = false) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        })
        val input = PlaybackInput("lease-1", "https://media.example/movie.mp4", positionMillis = 0)
        var systemBack: (() -> Unit)? = null
        val scene = ImageComposeScene(width, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme(television) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                .background(org.viptv.app.theme.Palette.background)) {
                if (state.screen == Screen.Settings) SettingsRoute(state, controller, browser, television, compact = width < 640 && !television,
                    desktop = desktop, entryBackChanged = { systemBack = it })
            } }
        }
        /** The player paused at 14:02 with the visit open, as [openPlayerSettings] leaves it. */
        fun open() {
            controller.mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = "1", localMode = true, playback = input,
                preferences = PreferencesSnapshot(), navBack = listOf(Screen.Home))
            controller.observePlaybackFor(input, PlayerFact.Progress(842_000, 3_132_000))
            controller.mutable.value = controller.state.value.copy(paused = true, settingsReturnsToPlayer = true, settingsEntrySection = PLAYER_SETTINGS_SECTION, screen = Screen.Settings)
        }
        fun assertBackInThePlayer(route: String) {
            val state = controller.state.value
            assertEquals(Screen.Player, state.screen, "$route did not return to the player")
            assertSame(input, state.playback, route)
            assertTrue(state.paused, "$route must leave the player paused")
            assertEquals(842_000L, input.positionMillis, "$route must return at the same position")
            assertFalse(state.settingsReturnsToPlayer)
        }
        override fun close() { scene.close(); scope.cancel() }
    }

    @Test fun phoneBackFromTheVisitReturnsToThePlayerByEveryRoute() = Visit(390, 844).use { ui ->
        for (route in listOf("in-page arrow", "Android Back", "navigateBack")) {
            ui.open(); frames(ui.scene)
            assertTrue(shows(ui.scene, "Back to player"), "The section shows its way back to the player")
            assertFalse(shows(ui.scene, "Switch profile"), "The visit opens the section, not the Settings list")
            assertEquals(SettingsPage(PLAYER_SETTINGS_SECTION, subpage = false), ui.controller.state.value.settingsPage)
            if (route == "in-page arrow") capture(ui.scene, "phone-subtitles-settings-from-player")
            val systemBack = assertNotNull(ui.systemBack, "Android Back is not routed to the player")
            when (route) {
                "in-page arrow" -> click(ui.scene, "Back to player")
                "Android Back" -> systemBack()
                else -> ui.controller.navigateBack()
            }
            frames(ui.scene)
            ui.assertBackInThePlayer(route)
            assertNull(ui.systemBack, "$route left Android Back routed into Settings")
        }
    }

    @Test fun desktopBackChevronAndEscapeReturnToThePlayer() = onEdt {
        Visit(1280, 800, desktop = true).use { ui ->
            for (route in listOf("Back chevron", "Escape", "Back to player")) {
                ui.open(); settle(ui.scene)
                assertTrue(shows(ui.scene, "Back to player"), "Wide layouts show the way back too")
                if (route == "Back chevron") capture(ui.scene, "desktop-subtitles-settings-from-player")
                when (route) {
                    // DesktopTitleBar's Back chevron calls navigateBack.
                    "Back chevron" -> ui.controller.navigateBack()
                    "Escape" -> {
                        assertTrue(ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)), "Escape is not handled")
                        ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
                    }
                    else -> click(ui.scene, "Back to player")
                }
                settle(ui.scene)
                ui.assertBackInThePlayer(route)
            }
        }
    }

    @Test fun escapeInATextFieldLeavesTheFieldBeforeLeavingSettings() = onEdt {
        Visit(1280, 800, desktop = true).use { ui ->
            ui.open(); settle(ui.scene)
            val field = ui.scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
                .first { it.config.contains(SemanticsActions.RequestFocus) && it.config.contains(SemanticsProperties.EditableText) &&
                    label(it).contains("Preferred audio language") }
            assertTrue(field.config[SemanticsActions.RequestFocus].action!!.invoke()); settle(ui.scene)
            fun escape() { ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown)); ui.scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp)) }
            escape(); settle(ui.scene)
            assertEquals(Screen.Settings, ui.controller.state.value.screen, "The first Escape only leaves the text field")
            escape(); settle(ui.scene)
            ui.assertBackInThePlayer("Escape twice")
        }
    }

    @Test fun tvRemoteBackFromTheVisitReturnsToThePlayer() = onEdt {
        Visit(1920, 1080, television = true).use { ui ->
            ui.open(); settle(ui.scene)
            assertTrue(shows(ui.scene, "Subtitles & audio"))
            capture(ui.scene, "tv-subtitles-settings-from-player")
            // MainActivity's BackHandler runs the route's Back for the TV remote's Back key.
            val remoteBack = assertNotNull(ui.systemBack, "TV remote Back is not routed to the player")
            remoteBack(); settle(ui.scene)
            ui.assertBackInThePlayer("TV remote Back")
        }
    }
}
