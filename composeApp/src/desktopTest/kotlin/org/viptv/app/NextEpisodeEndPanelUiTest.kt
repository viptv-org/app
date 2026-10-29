package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect as SkiaRect
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NextEpisodeEndPanelUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin, description = "The crew regroups after the storm.")
    private val frameColor = Color(0xFF2E7DB8)

    @Test fun phoneNextDuringPlaybackPlaysTheNextEpisodeWithoutOpeningThePanel() {
        val played = mutableListOf<Card>()
        val player = Player(844, 390, phone = true, next = episode(25)) { played += it }
        try {
            player.frames()
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(900_000, 1_800_000))
            player.click("Next episode")
            assertEquals(listOf(episode(25)), played)
            player.frames(20)
            assertFalse(player.hasText("Up next"), "Next during playback must not open the panel")
            assertEquals(1, played.size)
        } finally { player.close() }
    }

    @Test fun phoneEndOfPlaybackOpensThePanelOverTheLastFrameOnceUntilTheNextEnd() {
        val played = mutableListOf<Card>()
        val insets = WindowInsets(right = 48.dp, bottom = 16.dp)
        val player = Player(844, 390, phone = true, next = episode(25), insets = insets) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(1_790_000, 1_800_000))
            assertFalse(player.hasText("Up next"))
            player.facts(PlayerFact.Status("Ended"))
            assertTrue(player.hasText("Up next")); assertTrue(player.hasText("25. Episode 25"))
            assertTrue(played.isEmpty(), "Opening the panel never starts the next episode")
            player.assertOverFrame(420, 120)
            player.assertActionsInside(rightInset = 48, bottomInset = 16)
            player.capture("phone-end-panel")
            player.click("Watch credits"); player.frames(40)
            assertFalse(player.hasText("Up next"), "Dismissing keeps the last frame without the panel")
            player.frames(60); assertFalse(player.hasText("Up next"), "A dismissed end panel does not reopen by itself")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            assertTrue(player.hasText("Up next"))
            player.click("Next episode")
            assertEquals(listOf(episode(25)), played)
        } finally { player.close() }
    }

    @Test fun finaleShowsNoPanelAndKeepsNextUnavailableUntilANextEpisodeIsKnown() {
        val played = mutableListOf<Card>()
        val player = Player(844, 390, phone = true, next = null) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.frames(40)
            assertFalse(player.hasText("Up next"), "No next episode: no automatic panel")
            assertTrue(player.hasText("Next episode unavailable"))
            assertTrue(player.action("Next episode").config.contains(SemanticsProperties.Disabled))
            assertTrue(played.isEmpty())
            player.next = episode(2); player.frames(60)
            assertTrue(player.hasText("Up next")); assertTrue(played.isEmpty())
        } finally { player.close() }
    }

    @Test fun desktopNextPlaysDirectlyAndTheEndOpensThePanelOverTheLastFrame() {
        val played = mutableListOf<Card>()
        val player = Player(1280, 720, phone = false, next = episode(25)) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(900_000, 1_800_000))
            player.click("Next episode")
            assertEquals(listOf(episode(25)), played); player.frames(20)
            assertFalse(player.hasText("Up next"))
            player.facts(PlayerFact.Status("Ended"))
            assertTrue(player.hasText("Up next")); assertTrue(player.hasText("25. Episode 25"))
            player.assertOverFrame(640, 200)
            player.capture("desktop-end-panel")
            player.action("Watch credits").config[SemanticsActions.RequestFocus].action!!.invoke(); player.frames()
            player.key(Key.Escape)
            assertFalse(player.hasText("Up next"), "Escape dismisses the panel")
            assertEquals(0, player.closed, "Escape with the panel open must not leave the player")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.click("Next episode")
            assertEquals(listOf(episode(25), episode(25)), played)
        } finally { player.close() }
    }

    @Test fun desktopMovieHasNoNextControlAndNoEndPanel() {
        val player = Player(1280, 720, phone = false, next = null, title = episode(1).copy(identity = MediaIdentity("film", "movie", emptyList()), parent_identity = null)) { }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(40)
            assertFalse(player.hasText("Up next"))
            assertTrue(player.nodes().none { player.label(it) == "Next episode" })
        } finally { player.close() }
    }

    @Test fun tvNextPlaysDirectlyAndTheEndOpensThePanelOverTheFullLastFrame() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = episode(25)) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Progress(900_000, 1_800_000))
            player.click("Next episode")
            assertEquals(listOf(episode(25)), played); player.frames(20)
            assertFalse(player.hasText("Play next episode"), "Next during playback must not open the panel")
            player.facts(PlayerFact.Status("Ended")); player.frames(60)
            assertTrue(player.hasText("Play next episode")); assertTrue(player.hasText("25. Episode 25"))
            player.assertOverFrame(1800, 1000); player.assertOverFrame(60, 60)
            player.capture("tv-end-panel")
            player.back(); player.frames(100)
            assertFalse(player.hasText("Play next episode")); player.frames(140); assertFalse(player.hasText("Play next episode"))
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(180)
            player.click("Play next episode")
            assertEquals(listOf(episode(25), episode(25)), played)
        } finally { player.close() }
    }

    @Test fun tvFinaleShowsNoPanelAndNoNextControl() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = null) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            assertFalse(player.hasText("Play next episode"))
            assertTrue(player.nodes().none { player.label(it) == "Next episode" })
            assertTrue(played.isEmpty())
            player.next = episode(2); player.frames(100)
            assertTrue(player.hasText("Play next episode")); assertTrue(played.isEmpty())
        } finally { player.close() }
    }

    @Test fun phoneCountdownReachesZeroAndPlaysTheNextEpisodeOnce() {
        val played = mutableListOf<Card>()
        val player = Player(844, 390, phone = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            assertTrue(player.hasText("Next episode in 10"), "The primary action shows the countdown")
            player.seconds(3.2)
            assertTrue(player.hasText("Next episode in 7")); assertTrue(played.isEmpty())
            player.capture("phone-end-panel-countdown")
            player.seconds(6.0); assertTrue(played.isEmpty(), "Nothing plays before 0")
            player.seconds(1.0)
            assertEquals(listOf(episode(25)), played, "At 0 it plays the next episode")
            player.seconds(12.0); assertEquals(1, played.size)
        } finally { player.close() }
    }

    @Test fun desktopCountdownPlaysAndPressingNextPlaysAtOnce() {
        val played = mutableListOf<Card>()
        val player = Player(1280, 720, phone = false, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.seconds(10.5)
            assertEquals(listOf(episode(25)), played)
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.seconds(1.2)
            player.click("Next episode in 9")
            assertEquals(listOf(episode(25), episode(25)), played)
            player.seconds(12.0); assertEquals(2, played.size)
        } finally { player.close() }
    }

    @Test fun tvCountdownReachesZeroAndPlaysTheNextEpisode() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            assertTrue(player.hasText("Next episode in 10")); assertFalse(player.hasText("Nothing plays on its own."))
            assertTrue(player.hasText("Starts automatically. Press any button to cancel."))
            player.seconds(3.0); assertTrue(player.hasText("Next episode in 7")); assertEquals(.3f, player.ring()!!, .001f)
            player.capture("tv-end-panel-countdown")
            player.seconds(3.0); assertTrue(player.hasText("Next episode in 4")); assertEquals(.6f, player.ring()!!, .001f, "The ring reports each second")
            player.seconds(4.0)
            assertEquals(listOf(episode(25)), played)
        } finally { player.close() }
    }

    @Test fun phoneWatchCreditsEscapeAndPointerEachStopTheCountdown() {
        val played = mutableListOf<Card>()
        val player = Player(844, 390, phone = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.click("Watch credits"); player.seconds(12.0)
            assertFalse(player.hasText("Up next")); assertTrue(played.isEmpty(), "Watch credits cancels")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); assertTrue(player.hasText("Next episode in 10"))
            player.press(420, 40); player.seconds(12.0)
            assertTrue(player.hasText("Up next")); assertTrue(player.hasText("Next episode")); assertTrue(played.isEmpty(), "A pointer press cancels")
            player.click("Watch credits")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.focus("Watch credits"); player.key(Key.Escape); player.seconds(12.0)
            assertFalse(player.hasText("Up next")); assertTrue(played.isEmpty(), "Escape cancels")
        } finally { player.close() }
    }

    @Test fun phoneBackAndSeekingStopTheCountdown() {
        val played = mutableListOf<Card>()
        val player = Player(844, 390, phone = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended"))
            player.back(); player.seconds(12.0)
            assertFalse(player.hasText("Up next")); assertTrue(played.isEmpty(), "Back cancels")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.seconds(2.0)
            player.facts(PlayerFact.Status("Playing")); player.seconds(12.0)
            assertTrue(played.isEmpty(), "Seeking cancels")
            player.facts(PlayerFact.Status("Ended")); player.seconds(12.0)
            assertTrue(played.isEmpty(), "A cancelled countdown doesn't restart"); assertTrue(player.hasText("Next episode"))
        } finally { player.close() }
    }

    @Test fun tvDpadAndBackEachStopTheCountdown() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            player.focus("Next episode in 10"); player.key(Key.DirectionRight); player.seconds(12.0)
            assertTrue(played.isEmpty(), "A D-pad press cancels")
            assertTrue(player.hasText("Play next episode"), "The panel stays, without the countdown")
            assertTrue(player.hasText("Nothing plays on its own."))
            player.back(); player.frames()
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            player.focus("Next episode in 10"); player.key(Key.MediaPlayPause); player.seconds(12.0)
            assertTrue(played.isEmpty(), "Play/pause cancels"); assertTrue(player.hasText("Play next episode"))
            player.back(); player.frames()
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.seconds(2.0)
            assertTrue(player.hasText("Next episode in 8"))
            player.back(); player.seconds(12.0)
            assertFalse(player.hasText("Play next episode")); assertTrue(played.isEmpty(), "Back cancels")
        } finally { player.close() }
    }

    @Test fun tvKeepWatchingSeekingAndPointerEachStopTheCountdown() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = episode(25), autoplay = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            player.click("Keep watching credits"); player.seconds(12.0)
            assertFalse(player.hasText("Play next episode")); assertTrue(played.isEmpty(), "Keep watching credits cancels")
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.seconds(1.0)
            player.facts(PlayerFact.Status("Playing")); player.seconds(12.0)
            assertTrue(played.isEmpty(), "Seeking cancels")
            player.back(); player.frames()
            player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            assertTrue(player.hasText("Next episode in 10"))
            player.press(400, 300); player.seconds(12.0)
            assertTrue(played.isEmpty(), "A pointer press cancels"); assertTrue(player.hasText("Play next episode"))
        } finally { player.close() }
    }

    @Test fun withTheSettingOffThePanelOpensWithoutACountdown() {
        val played = mutableListOf<Card>()
        val phone = Player(844, 390, phone = true, next = episode(25), autoplay = false) { played += it }
        try {
            phone.frames(); phone.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); phone.seconds(12.0)
            assertTrue(phone.hasText("Up next")); assertTrue(phone.hasText("Next episode")); assertTrue(played.isEmpty())
        } finally { phone.close() }
        val tv = Player(1920, 1080, television = true, next = episode(25), autoplay = false) { played += it }
        try {
            tv.frames(); tv.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); tv.seconds(12.0)
            assertTrue(tv.hasText("Play next episode")); assertTrue(tv.hasText("Nothing plays on its own.")); assertTrue(played.isEmpty())
        } finally { tv.close() }
    }

    @Test fun finaleMovieAndLiveNeverCountDown() {
        val played = mutableListOf<Card>()
        val movie = episode(1).copy(identity = MediaIdentity("film", "movie", emptyList()), parent_identity = null)
        val channel = episode(1).copy(identity = MediaIdentity("channel-7", "channel", emptyList()), parent_identity = null)
        listOf(Triple(true, null, episode(24)), Triple(false, null, episode(24)), Triple(true, episode(25), movie),
            Triple(false, episode(25), movie), Triple(true, episode(25), channel), Triple(false, episode(25), channel)).forEach { (phone, next, title) ->
            val player = Player(if (phone) 844 else 1920, if (phone) 390 else 1080, phone = phone, television = !phone, next = next,
                title = title, autoplay = true) { played += it }
            try {
                player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.seconds(12.0)
                assertTrue(played.isEmpty(), "${title.identity.kind} with next ${next?.title} must not play on its own")
                assertFalse(player.nodes().any { player.text(it).startsWith("Next episode in") })
            } finally { player.close() }
        }
        assertTrue(nextEpisodeAutoplays(true, episode(24), live = false))
        assertFalse(nextEpisodeAutoplays(true, episode(24), live = true), "Live never counts down")
        assertFalse(nextEpisodeAutoplays(false, episode(24), live = false))
    }

    @Test fun reducedMotionKeepsTheNumberWithoutTheRing() {
        val played = mutableListOf<Card>()
        val player = Player(1920, 1080, television = true, next = episode(25), autoplay = true, reduceMotion = true) { played += it }
        try {
            player.frames(); player.facts(PlayerFact.Status("Playing"), PlayerFact.Status("Ended")); player.frames(60)
            player.seconds(3.0); assertTrue(player.hasText("Next episode in 7")); assertNull(player.ring(), "No ring animation")
            player.seconds(7.0); assertEquals(listOf(episode(25)), played)
        } finally { player.close() }
    }

    private inner class Player(val width: Int, val height: Int, phone: Boolean = false, television: Boolean = false, next: Card?,
        insets: WindowInsets = WindowInsets(0, 0, 0, 0), title: Card = episode(24), autoplay: Boolean = false, reduceMotion: Boolean = false,
        play: (Card) -> Unit) {
        var next by mutableStateOf(next)
        private val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
        private var back: (() -> Unit)? = null
        var closed = 0
        private var time = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television = television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                PlayerScreen(null, true, null, {}, {}, { closed++ }, title = title, phone = phone, television = television,
                    canvasColor = Color.Transparent, externalFacts = facts,
                    tvExtras = TvPlaybackExtras(episodes = listOf(title), nextEpisode = this@Player.next),
                    playEpisode = play, backChanged = { back = it }, playbackKey = "current", controlInsets = insets,
                    autoplayNext = autoplay, reduceMotion = reduceMotion)
            }
        } }
        fun frames(start: Int = 0) = repeat(16) { time = maxOf(time, start * 16_666_667L) + 16_666_667L; scene.render(time).close(); Thread.sleep(4) }
        fun seconds(value: Double) {
            time += (value * 1_000_000_000).toLong()
            repeat(4) { time += 1_000_000; scene.render(time).close(); Thread.sleep(4) }
        }
        fun press(x: Int, y: Int) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x.toFloat(), y.toFloat()))
            scene.sendPointerEvent(PointerEventType.Release, Offset(x.toFloat(), y.toFloat())); frames()
        }
        fun focus(value: String) { action(value).config[SemanticsActions.RequestFocus].action!!.invoke(); frames() }
        fun facts(vararg values: PlayerFact) { values.forEach { assertTrue(facts.tryEmit(it)); frames() }; frames() }
        fun back() { back!!.invoke() }
        fun key(key: Key) {
            scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown)); scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp)); frames()
        }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        fun label(node: SemanticsNode) = text(node).ifEmpty { node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }
        fun hasText(value: String) = nodes().any { text(it) == value }
        fun ring() = nodes().firstNotNullOfOrNull { node ->
            node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.takeIf { it.range == 0f..1f }?.current }
        fun action(value: String) = nodes().single { label(it) == value && it.config.contains(SemanticsActions.OnClick) }
        fun click(value: String) { assertTrue(action(value).config[SemanticsActions.OnClick].action!!.invoke()); frames() }
        private fun composite(still: SkiaImage? = null): SkiaImage {
            val surface = Surface.makeRasterN32Premul(width, height)
            if (still == null) surface.canvas.clear(frameColor.toArgb()) else {
                val crop = minOf(still.width.toFloat() / width, still.height.toFloat() / height)
                val w = width * crop; val h = height * crop
                surface.canvas.drawImageRect(still, SkiaRect.makeXYWH((still.width - w) / 2, (still.height - h) / 2, w, h),
                    SkiaRect.makeWH(width.toFloat(), height.toFloat()))
            }
            scene.render(time).use { surface.canvas.drawImage(it, 0f, 0f) }
            return surface.makeImageSnapshot()
        }
        private fun pixel(x: Int, y: Int) = composite().use { image -> Bitmap.makeFromImage(image).use { it.getColor(x, y) } }
        fun assertOverFrame(x: Int, y: Int) {
            val color = pixel(x, y)
            assertNotEquals(Palette.background.toArgb(), color, "($x, $y) shows the app background instead of the frame")
            val red = color shr 16 and 0xFF; val green = color shr 8 and 0xFF; val blue = color and 0xFF
            val frame = frameColor.toArgb()
            assertTrue(blue > red && blue > green && blue >= 16,
                "($x, $y) = ${Integer.toHexString(color)} does not show the last frame ${Integer.toHexString(frame)}")
        }
        fun assertActionsInside(rightInset: Int, bottomInset: Int) {
            val actions = nodes().filter { it.config.contains(SemanticsActions.OnClick) && it.boundsInWindow.width < width }
            assertTrue(actions.isNotEmpty())
            actions.forEach { assertTrue(it.boundsInWindow.right <= width - rightInset && it.boundsInWindow.bottom <= height - bottomInset,
                "${label(it)} at ${it.boundsInWindow} leaves the safe area") }
        }
        fun capture(name: String) {
            val path = System.getenv("VIPTV_NEXT_EPISODE_CAPTURE_DIR") ?: return
            val still = SkiaImage.makeFromEncoded(File("src/commonMain/composeResources/files/ui-preview/player.jpg").readBytes())
            File(path).mkdirs(); composite(still).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
        fun close() = scene.close()
    }
}
