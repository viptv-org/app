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
import kotlin.test.*

/**
 * app#41: the end of a series episode opens the next-episode panel by itself, over the last frame;
 * the Next episode control during playback goes straight to the next episode.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class NextEpisodeEndPanelUiTest {
    private val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
    private val parent = MediaIdentity("private-show", "series", emptyList())
    private fun episode(number: Long) = Card(MediaIdentity("private-$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        metadata = CardMetadata(runtime_minutes = 52), provider_id = "addon:7", title = "Episode $number", badges = emptyList(),
        parent_identity = parent, metadata_origin = origin, description = "The crew regroups after the storm.")
    /** A distinctive stand-in for the video's last frame, so pixels prove what is drawn over it. */
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
            // Seeking back and reaching the end again offers it again.
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
            // The controller asks again at the end; a next episode found then still gets its panel.
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
            // A headless scene has no focused window, so give the panel's first action focus as the window would.
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
            // The frame stays full screen: no app background where the old shrunken frame ended.
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
            // The controller asks again at the end; a next episode found then still gets its panel.
            player.next = episode(2); player.frames(100)
            assertTrue(player.hasText("Play next episode")); assertTrue(played.isEmpty())
        } finally { player.close() }
    }

    private inner class Player(val width: Int, val height: Int, phone: Boolean = false, television: Boolean = false, next: Card?,
        insets: WindowInsets = WindowInsets(0, 0, 0, 0), title: Card = episode(24), play: (Card) -> Unit) {
        var next by mutableStateOf(next)
        private val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 16)
        private var back: (() -> Unit)? = null
        var closed = 0
        private var time = 0L
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(television = television) {
            // The app background behind a transparent player canvas: the canvas punches through it to the
            // video layer underneath, as on web. [composite] then lays the result over the last frame.
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                PlayerScreen(null, true, null, {}, {}, { closed++ }, title = title, phone = phone, television = television,
                    canvasColor = Color.Transparent, externalFacts = facts,
                    tvExtras = TvPlaybackExtras(episodes = listOf(title), nextEpisode = this@Player.next),
                    playEpisode = play, backChanged = { back = it }, playbackKey = "current", controlInsets = insets)
            }
        } }
        fun frames(start: Int = 0) = repeat(16) { time = maxOf(time, start * 16_666_667L) + 16_666_667L; scene.render(time).close(); Thread.sleep(4) }
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
        fun action(value: String) = nodes().single { label(it) == value && it.config.contains(SemanticsActions.OnClick) }
        fun click(value: String) { assertTrue(action(value).config[SemanticsActions.OnClick].action!!.invoke()); frames() }
        /** The rendered player over the video layer showing [still], or the stand-in colour. */
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
        /** The pixel is the last frame under a translucent scrim: never the app background, never opaque black. */
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
        /** Evidence: a real still stands in for the last frame. */
        fun capture(name: String) {
            val path = System.getenv("VIPTV_NEXT_EPISODE_CAPTURE_DIR") ?: return
            val still = SkiaImage.makeFromEncoded(File("src/commonMain/composeResources/files/ui-preview/player.jpg").readBytes())
            File(path).mkdirs(); composite(still).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
        fun close() = scene.close()
    }
}
