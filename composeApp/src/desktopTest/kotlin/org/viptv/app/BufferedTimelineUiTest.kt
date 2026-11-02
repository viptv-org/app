package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.player.PlaybackSlider
import org.viptv.app.features.player.PlaybackTimeline
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TimelineSegment
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.features.player.TvPlaybackTimeline
import org.viptv.app.features.player.TvPlayerScene
import org.viptv.app.features.player.TvPlayerState
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import org.viptv.app.player.BufferedRange
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class BufferedTimelineUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private val accent = Palette.accent.toArgb()
    // White 50% over the inactive bar: .3 white over black for the pointer slider, .22 on the TV timeline.
    private val pointerBase = 0xFF4D4D4D.toInt()
    private val pointerBand = 0xFFA6A6A6.toInt()
    private val tvBase = 0xFF383838.toInt()
    private val tvBand = 0xFF9B9B9B.toInt()

    @Test fun theBandSitsBetweenThePlayheadAndTheBufferedEnd() {
        for (width in intArrayOf(390, 1280)) {
            val scene = timeline(width, listOf(BufferedRange(0, 120_000)))
            try {
                frames(scene)
                capture(scene, "timeline-$width")
                val track = track(scene)
                assertEquals(1, track.bands.size, "one reported range at $width dp: $track")
                assertFraction(track, track.playhead, .25f, "the played fill ends at the playhead, $width dp")
                assertFraction(track, track.bands.single().last, .5f, "the band ends at the buffered end, $width dp")
                assertTrue(track.bands.single().first >= track.playhead - 1,
                    "the band starts behind the played fill at $width dp: $track")
            } finally { scene.close() }
        }
    }

    @Test fun aGapAfterASeekStaysAGap() {
        val scene = timeline(1280, listOf(BufferedRange(0, 120_000), BufferedRange(150_000, 210_000)))
        try {
            frames(scene)
            capture(scene, "timeline-gap")
            val track = track(scene)
            assertEquals(2, track.bands.size, "two reported ranges draw two bands: $track")
            assertFraction(track, track.bands[0].last, .5f, "the first band ends where the renderer stops holding media")
            assertFraction(track, track.bands[1].first, .625f, "the second band starts at its own range")
            assertFraction(track, track.bands[1].last, .875f, "the second band ends at its own range")
        } finally { scene.close() }
    }

    @Test fun theBandLeavesTheBarAndThePlayheadWhereTheyAre() {
        val plain = timeline(1280, emptyList()).rendered { frames(it); track(it) }
        val filled = timeline(1280, listOf(BufferedRange(0, 120_000))).rendered { frames(it); track(it) }
        assertTrue(abs(plain.start - filled.start) <= 3, "the bar starts where it did: $plain vs $filled")
        assertTrue(abs(plain.end - filled.end) <= 3, "the bar ends where it did: $plain vs $filled")
        assertTrue(abs(plain.playhead - filled.playhead) <= 3, "the playhead does not move: $plain vs $filled")
    }

    @Test fun theBandStaysUnderTheSkipMarkers() {
        val scene = scene(1280, 200) {
            PlaybackTimeline(60_000, 240_000, {}, skips = listOf(TvSkipRange(120_000, 180_000, TvSkipKind.Intro)),
                buffered = listOf(BufferedRange(0, 180_000)))
        }
        try {
            frames(scene)
            capture(scene, "timeline-skip-and-buffer")
            val track = track(scene)
            assertEquals(1, track.bands.size, "the band shows where no marker covers it: $track")
            assertFraction(track, track.bands.single().last, .5f, "the band ends where the intro marker starts")
            val marker = runs(track.row) { near(it, Palette.segmentIntro.toArgb()) }.maxByOrNull { it.last - it.first }
            assertNotNull(marker, "the intro marker is drawn over the band: $track")
            assertFraction(track, marker.first, .5f, "the intro marker starts at its own range")
            assertFraction(track, marker.last, .75f, "the intro marker ends at its own range")
        } finally { scene.close() }
    }

    // One mapping for the played fill, the buffered band, the skip marker and the thumb: the bar the thumb
    // travels across. A boundary at 0 %, 50 % and 100 % must land on the thumb's own x there.
    @Test fun theBandTheMarkerAndTheThumbShareOneMapping() {
        val scene = sliderScene(.25f, buffered = listOf(BufferedRange(0, 120_000)), segments = listOf(marker(120_000, 180_000)))
        val track = scene.rendered { frames(it); track(it) }
        assertSameFraction(track, track.bands.single().last + 1, .5f, "the band ends at the thumb centre: $track")
        assertSameFraction(track, track.marker.first, .5f, "the marker starts at the thumb centre: $track", 3)
        assertTrue(track.row.take(track.start).all { (it and 0xFFFFFF) == 0 }, "no layer paints left of the bar: $track")
        for (fraction in listOf(0f, .5f, 1f)) {
            assertSameFraction(track, sliderScene(fraction).rendered { frames(it); track(it) }.playhead, fraction, "the thumb sits at $fraction")
        }
    }

    @Test fun theTvBandTheMarkerAndTheThumbShareOneMapping() {
        val track = tvScene(60_000, buffered = listOf(BufferedRange(0, 120_000)), skips = listOf(TvSkipRange(120_000, 180_000)))
            .rendered { frames(it); track(it, tvBase, tvBand) }
        assertSameFraction(track, track.bands.single().last + 1, .5f, "the band ends at the thumb centre: $track")
        assertSameFraction(track, track.marker.first, .5f, "the marker starts at the thumb centre: $track", 3)
        for (fraction in listOf(0f, .5f, 1f)) {
            val thumb = tvScene((240_000 * fraction).toLong()).rendered { frames(it); track(it, tvBase, tvBand) }.playhead
            assertSameFraction(track, thumb, fraction, "the thumb sits at $fraction")
        }
    }

    @Test fun theBandDoesNotChangeSeeking() {
        for (buffered in listOf(emptyList<BufferedRange>(), listOf(BufferedRange(0, 120_000)))) {
            var target: Long? = null
            val scene = scene(1280, 200) { PlaybackTimeline(60_000, 240_000, { target = it }, buffered = buffered) }
            try {
                frames(scene)
                val slider = nodes(scene).first { it.config.contains(SemanticsActions.SetProgress) }
                slider.config[SemanticsActions.SetProgress].action!!.invoke(.5f)
                assertEquals(120_000L, target, "a seek to the middle is the same with and without a band: ${buffered.size} ranges")
            } finally { scene.close() }
        }
    }

    @Test fun aRangeTheTimelineCannotPlaceDrawsNothing() {
        val scene = timeline(390, listOf(BufferedRange(300_000, 360_000)))
        try {
            frames(scene)
            assertTrue(track(scene).bands.isEmpty(), "a range past the media's length draws no band")
        } finally { scene.close() }
    }

    @Test fun liveDrawsNoBandOnTheTvTimeline() {
        val state = TvPlayerState(PlaybackInput("live", "https://media.example/live.m3u8", live = true),
            listOf(PlayerFact.Progress(60_000, null), PlayerFact.Buffered(listOf(BufferedRange(0, 120_000)))), TvPlayerScene.Controls)
        val scene = scene(1920, 1080) {
            Box(Modifier.fillMaxSize()) { TvPlaybackTimeline(state, TvPlaybackExtras(), 1f, FocusRequester(), FocusRequester(), null, live = true) }
        }
        try {
            frames(scene)
            val track = track(scene, tvBase, tvBand)
            assertTrue(track.end > track.start, "the live timeline still draws its bar")
            assertTrue(track.bands.isEmpty(), "live has no buffered band: $track")
        } finally { scene.close() }
    }

    @Test fun aNewPlaybackKeyDropsThePreviousBuffer() {
        var key by mutableStateOf("first")
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 8)
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, phone = true, preview = PlayerFact.Progress(60_000, 240_000),
                playbackKey = key, externalFacts = facts)
        } }
        try {
            frames(scene)
            assertTrue(track(scene).bands.isEmpty(), "no band before the renderer reports one")
            assertTrue(facts.tryEmit(PlayerFact.Buffered(listOf(BufferedRange(0, 120_000)))))
            frames(scene)
            assertTrue(track(scene).bands.isNotEmpty(), "the band appears when the renderer reports one")
            capture(scene, "phone-buffered")
            key = "second"
            frames(scene)
            assertTrue(track(scene).bands.isEmpty(), "the previous item's buffer is gone")
        } finally { scene.close() }
    }

    @Test fun theTvTimelineDrawsTheBandUnderItsPlayedFill() {
        val input = PlaybackInput("tv", "https://media.example/clip", durationMillis = 240_000)
        val state = TvPlayerState(input, listOf(PlayerFact.Progress(60_000, 240_000), PlayerFact.Buffered(listOf(BufferedRange(0, 120_000)))),
            TvPlayerScene.Controls)
        val scene = scene(1920, 1080) {
            Box(Modifier.fillMaxSize()) { TvPlaybackTimeline(state, TvPlaybackExtras(), 1f, FocusRequester(), FocusRequester(), null) }
        }
        try {
            frames(scene)
            capture(scene, "tv-buffered")
            val track = track(scene, tvBase, tvBand)
            assertEquals(1, track.bands.size, "one reported range on the TV timeline: $track")
            assertFraction(track, track.playhead, .25f, "the TV played fill ends at the playhead")
            assertFraction(track, track.bands.single().last, .5f, "the TV band ends at the buffered end")
        } finally { scene.close() }
    }

    private fun timeline(width: Int, buffered: List<BufferedRange>) = scene(width, 200) {
        PlaybackTimeline(60_000, 240_000, {}, buffered = buffered)
    }

    private fun marker(startMillis: Long, endMillis: Long) = TimelineSegment(startMillis, endMillis, TvSkipKind.Intro, 240_000)

    private fun sliderScene(value: Float, buffered: List<BufferedRange> = emptyList(), segments: List<TimelineSegment> = emptyList()) = scene(400, 100) {
        PlaybackSlider(value, {}, Modifier.fillMaxSize(), segments = segments, buffered = buffered, duration = 240_000)
    }

    private fun tvScene(position: Long, buffered: List<BufferedRange> = emptyList(), skips: List<TvSkipRange> = emptyList()): ImageComposeScene {
        val state = TvPlayerState(PlaybackInput("tv", "https://media.example/clip", durationMillis = 240_000),
            listOf(PlayerFact.Progress(position, 240_000), PlayerFact.Buffered(buffered)), TvPlayerScene.Controls)
        return scene(1920, 1080) {
            Box(Modifier.fillMaxSize()) { TvPlaybackTimeline(state, TvPlaybackExtras(skips = skips), 1f, FocusRequester(), FocusRequester(), null) }
        }
    }

    private fun scene(width: Int, height: Int, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme(false) { Surface(Modifier.fillMaxSize(), color = Color.Black) { content() } } }

    private data class Track(val start: Int, val playhead: Int, val end: Int, val bands: List<IntRange>, val row: List<Int>,
        val marker: IntRange = IntRange.EMPTY, val knob: IntRange = IntRange.EMPTY, val fill: IntRange = IntRange.EMPTY)

    private fun track(scene: ImageComposeScene, base: Int = pointerBase, band: Int = pointerBand): Track {
        val (width, colors) = pixels(scene)
        val height = colors.size / width
        val row = (0 until height).maxBy { y ->
            (0 until width).count { x -> near(colors[y * width + x], accent) || near(colors[y * width + x], base) }
        }
        val pixels = (0 until width).map { colors[row * width + it] }
        val fill = runs(pixels) { near(it, accent) }.maxByOrNull { it.last - it.first }
        val held = runs(pixels) { near(it, base) }.filter { it.last - it.first >= 8 }
        val bands = runs(pixels) { near(it, band, 5) }.filter { it.last - it.first >= 8 }
        val knob = runs(pixels) { near(it, 0xFFFFFFFF.toInt(), 2) }.maxByOrNull { it.last - it.first } ?: IntRange.EMPTY
        val painted = listOfNotNull(fill) + held + bands
        assertTrue(painted.isNotEmpty(), "no timeline bar to measure")
        return Track(fill?.first ?: painted.minOf { it.first }, (knob.first + knob.last + 1) / 2, painted.maxOf { it.last }, bands, pixels,
            runs(pixels) { near(it, Palette.segmentIntro.toArgb()) }.maxByOrNull { it.last - it.first } ?: IntRange.EMPTY, knob,
            fill ?: IntRange.EMPTY)
    }

    // A boundary and the thumb's centre agree at density 1; a marker's 2 dp rounded start costs 3 px.
    private fun assertSameFraction(track: Track, actual: Int, fraction: Float, what: String, tolerance: Int = 2) {
        val expected = track.start + (track.end - track.start) * fraction
        assertTrue(abs(actual - expected) <= tolerance, "$what: expected x=${expected.toInt()} at ${fraction * 100}%, was $actual (bar ${track.start}..${track.end})")
    }

    private fun assertFraction(track: Track, actual: Int, fraction: Float, what: String) {
        val expected = track.start + (track.end - track.start) * fraction
        assertTrue(abs(actual - expected) <= 4, "$what: expected x=${expected.toInt()}, was $actual (bar ${track.start}..${track.end})")
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun runs(pixels: List<Int>, match: (Int) -> Boolean): List<IntRange> {
        val found = mutableListOf<IntRange>()
        var start = -1
        pixels.forEachIndexed { index, color ->
            if (match(color)) { if (start < 0) start = index }
            else if (start >= 0) { found += start until index; start = -1 }
        }
        if (start >= 0) found += start until pixels.size
        return found
    }

    private fun pixels(scene: ImageComposeScene): Pair<Int, IntArray> {
        val image = scene.render(frame++ * 16_666_667L)
        val bitmap = Bitmap.makeFromImage(image)
        val width = bitmap.width
        val colors = IntArray(width * bitmap.height) { bitmap.getColor(it % width, it / width) }
        image.close(); bitmap.close()
        return width to colors
    }

    private fun near(actual: Int, expected: Int, tolerance: Int = 4) = (0..2).all { channel ->
        abs(((actual shr (channel * 8)) and 0xFF) - ((expected shr (channel * 8)) and 0xFF)) <= tolerance
    }

    private var frame = 0L
    private fun frames(scene: ImageComposeScene) = repeat(30) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    private fun capture(scene: ImageComposeScene, name: String) {
        System.getenv("VIPTV_BUFFERED_TIMELINE_CAPTURE_DIR")?.let { path ->
            File(path).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
        }
    }

    private inline fun <T> ImageComposeScene.rendered(block: (ImageComposeScene) -> T): T = try { block(this) } finally { close() }
}
