package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.live.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LivePreviewClockUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val clock = LiveClock(originMillis = 1_700_000_000_000, nowMillis = 1_700_000_000_000)
    private fun channel() = LiveChannel(
        Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "Channel 1", badges = emptyList()),
        number = "1", id = "one", connectionId = "m3u:1")
    private fun program(title: String, start: Int, end: Int) = LiveProgram("p1", title, start, end, description = "The evening bulletin.")

    private fun scene(width: Int, height: Int, television: Boolean, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
            CompositionLocalProvider(LocalLiveClock provides clock) {
                ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
            }
        }

    private fun phone(name: String, program: LiveProgram, now: Int): List<String> {
        val scene = scene(390, 400, false) { LivePreview(channel(), program, now, phone = true, television = false, scale = 1f, watch = {}, favourite = null) }
        return try {
            frames(scene)
            capture(scene, "phone-390-$name")
            texts(scene)
        } finally { scene.close() }
    }

    private fun tv(name: String, program: LiveProgram, now: Int): List<String> {
        val scene = scene(1920, 1080, true) { TvLivePreview(channel(), program, now, 1f, favorite = null, watch = {}) }
        return try {
            frames(scene)
            capture(scene, "tv-1920-$name")
            texts(scene)
        } finally { scene.close() }
    }

    @Test fun phoneFramesAnAiringProgrammeAsLiveAndCountsDown() {
        val texts = phone("on-air", program("Evening News", 0, 30), now = 15)
        assertTrue(texts.any { it.contains("LIVE") })
        assertTrue(texts.any { it.contains("15 min left") })
        assertTrue(progressBars(phoneScene("Evening News", 0, 30, 15)))
    }

    @Test fun phoneReplacesTheLiveFramingOfAFutureProgrammeWithItsStartAndLength() {
        val texts = phone("upcoming", program("The Late Show", 90, 150), now = 0)
        assertTrue(texts.none { it.contains("LIVE") }, "a programme that has not started is not live")
        assertEquals(1, texts.count { it.contains("Starts at ") && it.contains(" · 60 min") }, "the line names the start and the programme's own length")
        assertFalse(progressBars(phoneScene("The Late Show", 90, 150, 0)))
    }

    @Test fun phoneReadsAnEndedProgrammeAsEndedWithNoBadgeOrBar() {
        val texts = phone("ended", program("Evening News", 0, 30), now = 60)
        assertTrue(texts.none { it.contains("LIVE") }, "a programme that ended is not live")
        assertTrue(texts.any { it.contains("Ended") })
        assertFalse(progressBars(phoneScene("Evening News", 0, 30, 60)))
    }

    @Test fun tvAppendsOnlyTheClockStateTheRangeDoesNotAlreadySay() {
        val airing = tv("on-air", program("Evening News", 0, 30), now = 15)
        assertTrue(airing.contains("LIVE"))
        assertTrue(airing.any { it.endsWith(" · 15 min left") })

        val future = tv("upcoming", program("The Late Show", 90, 150), now = 0)
        assertFalse(future.contains("LIVE"), "the badge marks only what is on air")
        assertTrue(future.any { it.endsWith(" · 60 min") })
        assertTrue(future.none { it.contains("Starts at") }, "the range already carries the start time")

        val ended = tv("ended", program("Evening News", 0, 30), now = 60)
        assertFalse(ended.contains("LIVE"))
        assertTrue(ended.any { it.endsWith(" · Ended") })
    }

    private data class Case(val name: String, val program: LiveProgram, val now: Int, val expected: String)

    @Test fun theDesktopPreviewAppendsTheSameClockStateToItsRange() {
        for (case in listOf(
            Case("on-air", program("Evening News", 0, 30), 15, " · 15 min left"),
            Case("upcoming", program("The Late Show", 90, 150), 0, " · 60 min"),
            Case("ended", program("Evening News", 0, 30), 60, " · Ended"))) {
            val scene = scene(1280, 420, false) { LivePreview(channel(), case.program, case.now, phone = false, television = false, scale = 1f, watch = {}, favourite = null) }
            try {
                frames(scene)
                assertTrue(texts(scene).any { it.endsWith(case.expected) }, "${case.name} preview ends its range line with ${case.expected}")
                capture(scene, "desktop-1280-${case.name}")
            } finally { scene.close() }
        }
    }

    private fun phoneScene(title: String, start: Int, end: Int, now: Int) =
        scene(390, 400, false) { LivePreview(channel(), program(title, start, end), now, phone = true, television = false, scale = 1f, watch = {}, favourite = null) }

    private fun progressBars(scene: ImageComposeScene) = try { frames(scene); nodes(scene).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null } }
        finally { scene.close() }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render(it * 16_666_667L).close() }
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text).filter { it.isNotEmpty() }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun capture(scene: ImageComposeScene, name: String) {
        val directory = System.getenv("VIPTV_LIVE_CAPTURE_DIR") ?: return
        File(directory).mkdirs()
        scene.render(1_000_000_000L).use { image -> image.encodeToData()!!.use { File(directory, "$name.png").writeBytes(it.bytes) } }
    }
}