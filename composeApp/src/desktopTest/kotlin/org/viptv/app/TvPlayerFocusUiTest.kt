@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.features.player.TvPlayerScene
import org.viptv.app.features.player.TvPlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

class TvPlayerFocusUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private val captures = File("/tmp/viptv-870").apply { mkdirs() }

    private val timeline = "Position"
    private val audio = "Audio & subtitles"

    private fun title(live: Boolean) = Card(MediaIdentity(if (live) "channel-1" else "movie-1", if (live) "channel" else "movie", emptyList()),
        provider_id = "test", title = if (live) "Garden Rescue" else "The Long Way Home",
        subtitle = if (live) "Live" else "S1 E2 · Please Don’t Go", badges = emptyList())

    private inner class Player(live: Boolean = false) : AutoCloseable {
        private var frame = 0L
        private var focusManager: FocusManager? = null
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(television = true) {
                focusManager = LocalFocusManager.current
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    TvPlayerScreen(null, true, null, {}, {}, {}, title(live), TvPlaybackExtras(), null, null, null, {},
                        scene = TvPlayerScene.Controls, playbackKey = "current",
                        initialFacts = if (live) emptyList() else listOf(PlayerFact.Progress(842_000, 3_132_000)))
                }
            }
        }

        fun until(what: String, condition: () -> Boolean) { frame = scene.renderUntil(from = frame, what = what, condition = condition) }

        fun settle(frames: Int = 24) = repeat(frames) { scene.render(frame++ * 16_666_667L).close() }

        fun press(key: Key) {
            val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
            // The desktop scene leaves the arrow keys to the app; a TV's platform moves focus itself.
            if (!consumed) focusManager!!.moveFocus(when (key) {
                Key.DirectionLeft -> FocusDirection.Left
                Key.DirectionRight -> FocusDirection.Right
                Key.DirectionUp -> FocusDirection.Up
                else -> FocusDirection.Down
            })
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        }

        fun focused(): SemanticsNode? = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }

        fun focusedLabel(): String? = focused()?.let(::label)

        fun progress(): Float? = nodes(scene).mapNotNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) }.firstOrNull()?.current

        fun timelineBounds() = focused()?.boundsInRoot

        fun capture(name: String): Pixels = scene.render(frame++ * 16_666_667L).use { image ->
            image.encodeToData()?.use { File(captures, "$name.png").writeBytes(it.bytes) }
            Pixels(Bitmap.makeFromImage(image))
        }

        override fun close() = scene.close()
    }

    @Test fun theChromeAppearsWithTheTimelineFocusedAndItsRingDrawn() = ui {
        Player().use { player ->
            player.until("the timeline to take focus") { player.focusedLabel()?.startsWith(timeline) == true }
            player.settle()
            val bounds = assertNotNull(player.timelineBounds(), "the seek bar holds focus")
            val pixels = player.capture("tv-player-timeline-focus")

            val ring = pixels.whiteAcross(bounds.left.toInt() + 24, bounds.right.toInt() - 24, bounds.top.toInt() - 3, bounds.top.toInt() + 2)
            assertTrue(ring > 500, "the focused seek bar draws the shared white ring, saw $ring white pixels")
        }
    }

    @Test fun theRemoteWalksFromTheSeekBarToThePillsAndBack() = ui {
        Player().use { player ->
            player.until("the timeline to take focus") { player.focusedLabel()?.startsWith(timeline) == true }
            val bounds = assertNotNull(player.timelineBounds())

            player.press(Key.DirectionUp)
            player.until("the audio pill to take focus") { player.focusedLabel() == audio }
            player.settle()
            val unfocusedRing = player.capture("tv-player-audio-focus")
                .whiteAcross(bounds.left.toInt() + 24, bounds.right.toInt() - 24, bounds.top.toInt() - 3, bounds.top.toInt() + 2)
            assertTrue(unfocusedRing < 100, "the ring follows focus off the seek bar, saw $unfocusedRing white pixels")

            player.press(Key.DirectionDown)
            player.until("the timeline to take focus again") { player.focusedLabel()?.startsWith(timeline) == true }
            player.settle()

            val before = assertNotNull(player.progress(), "the seek bar reports its position")
            player.press(Key.DirectionRight)
            player.until("the seek bar to step forward") { player.progress() != before }

            player.press(Key.DirectionDown)
            player.until("the chrome to hide") { player.focused() != null && player.focusedLabel().isNullOrEmpty() }
            player.press(Key.DirectionUp)
            player.until("Up to reveal the chrome on its pill") { player.focusedLabel() == audio }
        }
    }

    @Test fun aLiveStreamFocusesItsAudioButton() = ui {
        Player(live = true).use { player ->
            player.until("the audio pill to take focus") { player.focusedLabel() == audio }
            player.settle()
            val bounds = assertNotNull(player.focused()?.boundsInRoot)
            val pixels = player.capture("tv-player-live-focus")

            val ring = pixels.whiteAcross(bounds.left.toInt() - 3, bounds.left.toInt() + 2, bounds.top.toInt() + 12, bounds.bottom.toInt() - 12)
            assertTrue(ring > 20, "the focused audio pill draws the shared white ring, saw $ring white pixels")
        }
    }
}

private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
    scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }

private fun label(node: SemanticsNode): String =
    node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        .ifBlank { node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }

private class Pixels(private val bitmap: Bitmap) {
    fun whiteAcross(left: Int, right: Int, top: Int, bottom: Int): Int {
        var white = 0
        for (y in top.coerceAtLeast(0)..bottom.coerceAtMost(bitmap.height - 1)) {
            for (x in left.coerceAtLeast(0)..right.coerceAtMost(bitmap.width - 1)) {
                if (bitmap.getColor(x, y) == White) white++
            }
        }
        return white
    }

    private companion object {
        const val White = 0xFFFFFFFF.toInt()
    }
}