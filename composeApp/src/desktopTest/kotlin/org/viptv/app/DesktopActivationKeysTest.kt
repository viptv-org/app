package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.InputSurface
import org.viptv.app.components.bindsActivationKeys
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopActivationKeysTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun onlyThePointerOnlyDesktopClientOmitsActivationKeys() {
        assertEquals(
            mapOf(InputSurface.Television to true, InputSurface.Touch to true,
                InputSurface.Browser to true, InputSurface.Desktop to false),
            InputSurface.entries.associateWith { it.bindsActivationKeys })
    }

    @Test fun theDesktopPlayerIgnoresPlaybackAndSkipMediaKeys() {
        val player = Player(activationKeys = false)
        try {
            player.key(Key.Spacebar)
            player.key(Key.MediaPlayPause)
            assertEquals(0, player.toggles, "a pointer press is the only way to pause on desktop")
            player.key(Key.MediaFastForward)
            player.key(Key.MediaRewind)
            assertTrue(player.shownTime() == "0:00", "media keys no longer seek on desktop, saw ${player.shownTime()}")
        } finally { player.close() }
    }

    @Test fun thePhoneAndBrowserPlayersKeepPlaybackKeys() {
        listOf(InputSurface.Touch, InputSurface.Browser).forEach { surface ->
            val player = Player(activationKeys = true)
            try {
                player.key(Key.Spacebar)
                player.key(Key.MediaPlayPause)
                assertEquals(2, player.toggles, "$surface toggles playback from Space and MediaPlayPause")
            } finally { player.close() }
        }
    }

    @Test fun theTelevisionPlayerKeepsPlaybackKeys() {
        val player = Player(television = true)
        try {
            player.frames()
            player.key(Key.Spacebar)
            player.key(Key.MediaPlayPause)
            assertEquals(2, player.toggles, "the TV remote keeps Space and MediaPlayPause")
        } finally { player.close() }
    }

    private class Player(television: Boolean = false, activationKeys: Boolean = true) : AutoCloseable {
        var toggles = 0
        private var time = 0L
        val scene = ImageComposeScene(1280, 720, Density(1f)) {
            ViptvTheme(television = television, activationKeys = activationKeys) {
                PlayerScreen(null, true, null, { toggles++ }, {}, {}, television = television,
                    preview = PlayerFact.Progress(0, 20_000))
            }
        }

        fun frames(count: Int = 16) = repeat(count) { scene.render(time).close(); time += 16_666_667L }

        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
            frames()
        }

        fun shownTime(): String? = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { part -> part.text } }
            .firstOrNull { it.matches(Regex("""\d+:\d{2}""")) }

        private fun flatten(node: SemanticsNode): List<SemanticsNode> =
            listOf(node) + node.children.flatMap(::flatten)

        override fun close() = scene.close()
    }
}
