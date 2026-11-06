package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.player.*
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PlayerSpaceActivationUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin)
    private var frame = 0L

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private inner class Player(val width: Int, val height: Int, private val phone: Boolean) {
        var toggles = 0
        var closed = 0
        var fullscreen = 0
        private val facts = kotlinx.coroutines.flow.MutableSharedFlow<PlayerFact>(replay = 1).apply {
            tryEmit(PlayerFact.Progress(768_000, 3_132_000))
        }
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, { toggles++ }, {}, { closed++ }, title = episode(24), phone = phone, externalFacts = facts,
                tvExtras = TvPlaybackExtras(episodes = listOf(episode(24)), nextEpisode = episode(25)), playEpisode = {},
                chooseSources = {}, toggleFullscreen = { fullscreen++ })
        } }
        fun frames(count: Int = 16) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        fun control(value: String) = nodes(scene).filter { it.config.contains(SemanticsActions.OnClick) }
            .firstOrNull { node -> texts(node).any { it == value } }
        fun focus(value: String) {
            assertNotNull(control(value), "No focusable \"$value\"").config[SemanticsActions.RequestFocus].action!!.invoke(); frames()
        }
        fun focusSurface() {
            val surface = assertNotNull(nodes(scene).firstOrNull { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Hide controls" },
                "No surface")
            surface.config[SemanticsActions.RequestFocus].action!!.invoke(); frames()
        }
        fun space() {
            scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(Key.Spacebar, KeyEventType.KeyUp)); frames()
        }
        fun shows(value: String) = nodes(scene).any { node -> texts(node).any { it == value } }
        fun panelOpen() = nodes(scene).any { it.config.getOrNull(SemanticsProperties.PaneTitle) == "Audio and subtitles" }
        fun close() = scene.close()
    }

    @Test fun spaceActivatesTheFocusedControlInsteadOfPlayback() {
        val desktop = Triple(1280, 800, false)
        val phone = Triple(390, 844, true)
        val cases = listOf(
            Triple(desktop, "Fill video", { p: Player -> p.shows("Fit video") }),
            Triple(desktop, "Close playback", { p: Player -> p.closed == 1 }),
            Triple(desktop, "Enter fullscreen", { p: Player -> p.fullscreen == 1 }),
            Triple(desktop, "Audio and subtitles", { p: Player -> p.panelOpen() }),
            Triple(phone, "Fill video", { p: Player -> p.shows("Fit video") }),
            Triple(phone, "Audio & subtitles", { p: Player -> p.panelOpen() }),
            Triple(phone, "Lock", { p: Player -> p.shows("Unlock controls") }))
        for ((size, label, activated) in cases) {
            val player = Player(size.first, size.second, size.third)
            try {
                player.frames()
                player.focus(label)
                player.space()
                assertEquals(0, player.toggles, "${size.first}×${size.second}: Space on \"$label\" must not toggle playback")
                assertTrue(activated(player), "${size.first}×${size.second}: Space did not activate \"$label\"")
            } finally { player.close() }
        }
    }

    @Test fun spaceOnThePlayButtonTogglesPlaybackOnce() {
        for (phone in listOf(false, true)) {
            val player = Player(if (phone) 390 else 1280, if (phone) 844 else 800, phone)
            try {
                player.frames()
                player.focus("Play")
                player.space()
                assertEquals(1, player.toggles, "Space on the play button toggles playback once")
            } finally { player.close() }
        }
    }

    @Test fun spaceTogglesPlaybackWhenNoControlHasFocus() {
        for (phone in listOf(false, true)) {
            val player = Player(if (phone) 390 else 1280, if (phone) 844 else 800, phone)
            try {
                player.frames()
                player.focusSurface()
                player.space()
                assertEquals(1, player.toggles, "Space over the player surface plays or pauses")
            } finally { player.close() }
        }
    }
}