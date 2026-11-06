package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import java.io.File
import kotlin.test.*
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.LiveBadge
import org.viptv.app.components.LiveRed
import org.viptv.app.components.withoutLiveTag
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.TvLivePreview
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity

class LiveBadgeTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val program = LiveProgram("p1", "Evening News", 0, 30)
    private val channel = LiveChannel(Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1",
        title = "Channel One", badges = listOf("LIVE")), number = "1", id = "one", connectionId = "m3u:1", programs = listOf(program))

    private fun scene(width: Int, height: Int, television: Boolean, content: @Composable () -> Unit): ImageComposeScene {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television) { Box(Modifier.fillMaxSize()) { content() } }
        }
        repeat(20) { scene.render(it * 16_666_667L).close() }
        return scene
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(false) }
    private fun drawsLive(scene: ImageComposeScene) = words(scene, "LIVE") > 0
    private fun words(scene: ImageComposeScene, word: String) = nodes(scene).count { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == word } == true
    }

    @Test fun theBadgeGroundsTheWordOnTheSameGlassAtEverySize() {
        for ((name, television, height) in listOf(Triple("phone", false, 20), Triple("TV", true, 32))) {
            val scene = scene(200, height * 2, television) { Box(Modifier.fillMaxSize().background(Color.White)) { LiveBadge(television = television) } }
            try {
                val pixels = scene.render(1_000_000_000L).use { Bitmap.makeFromImage(it) }
                val drawn = (0 until height * 2).flatMap { y -> (0 until 200).map { x -> pixels.getColor(x, y) } }
                    .filter { it != Color.White.toArgb() }
                val glass = drawn.groupingBy { it }.eachCount().maxBy { it.value }.key
                assertEquals(GlassOverWhite, glass and 0xFFFFFF, "$name: most of the badge is bg at 0.72 over what is behind it")
            } finally { scene.close() }
        }
    }

    @Test fun everyLayoutDrawsThatOneBadge() {
        val surfaces = listOf(
            Triple(390 to 260, false, @Composable { LivePreview(channel, program, 15, phone = true, television = false, scale = 1f, watch = {}, favourite = null) }),
            Triple(1920 to 1080, true, @Composable { TvLivePreview(channel, program, 15, 1f, favorite = null, watch = {}) }),
        )
        for ((size, television, content) in surfaces) {
            val scene = scene(size.first, size.second, television, content)
            try {
                assertTrue(drawsLive(scene), "${size.first}px: the badge draws the word")
                val pixels = scene.render(1_000_000_000L).use { Bitmap.makeFromImage(it) }
                val box = nodes(scene).first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "LIVE" } == true }.boundsInRoot
                val middle = box.center.y.toInt()
                val left = box.left.toInt()
                val dot = (2..12).map { step -> pixels.getColor((left - step).coerceAtLeast(0), middle) }
                assertTrue(dot.any { it == LiveRed.toArgb() }, "${size.first}px: the badge carries the LIVE red dot")
            } finally { scene.close() }
        }
    }

    @Test fun aCardLiveTagDoesNotSayLiveTwiceBesideTheBadge() {
        val tagged = channel.copy(card = channel.card.copy(badges = listOf("LIVE", "1080p")))
        val surfaces = listOf(
            Triple(390 to 260, false, @Composable { LivePreview(tagged, program, 15, phone = true, television = false, scale = 1f, watch = {}, favourite = null) }),
            Triple(1920 to 1080, true, @Composable { TvLivePreview(tagged, program, 15, 1f, favorite = null, watch = {}) }),
        )
        for ((size, television, content) in surfaces) {
            val scene = scene(size.first, size.second, television, content)
            try {
                assertEquals(1, words(scene, "LIVE"), "${size.first}px: the badge is the only LIVE on the surface")
            } finally { scene.close() }
        }
        assertEquals(listOf("1080p"), listOf("LIVE", "1080p").withoutLiveTag(), "the tag row keeps every other tag")
        assertEquals(listOf("1080p"), listOf("live", "1080p").withoutLiveTag(), "the tag is matched however the add-on spells it")
    }

    @Test fun nothingElseDrawsALiveBadgeOfItsOwn() {
        val root = File("src/commonMain/kotlin/org/viptv/app")
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val retiredReds = files.filter { file -> file.name != "LiveBadge.kt" && Regex("0xFFE8434F|0xFFE5484D|0xFFFF5A4E").containsMatchIn(file.readText()) }
            .map { it.name }
        assertEquals(emptyList(), retiredReds, "components/LiveBadge.kt owns the badge's fill")
        val declares = files.filter { Regex("""val LiveRed""").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(listOf("LiveBadge.kt"), declares, "one LIVE red, in the badge")
        val badges = files.filter { Regex("""fun LiveBadge""").containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(listOf("LiveBadge.kt"), badges, "one LIVE badge, in components")
    }

    private companion object {
        // bg #0B0B0C at 0.72 over white.
        const val GlassOverWhite = 0x4F4F50
    }
}
