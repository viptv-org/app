@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.HomeGenrePicker
import org.viptv.app.components.LocalHomeGenres
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.preview.PreviewData
import org.viptv.app.preview.PreviewTvHome
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.HomeSnapshot

// Every line drawn over artwork keeps 4.5:1 against the brightest frame, so the fixture behind the copy is flat white.
class TextOverArtworkContrastTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val white = PreviewData.hero.copy(landscape = "https://artwork.test/white.jpg", poster = null, logo = null)
    private val home = PreviewData.home.copy(hero = white, featured = emptyList())
    private val tvHome = PreviewTvHome.home.copy(hero = white, featured = emptyList())

    private class Surface(val name: String, val width: Int, val height: Int, val television: Boolean,
        val home: HomeSnapshot, val copy: List<String>, val hero: List<String>, val tablet: Boolean = false, val desktop: Boolean = false)

    private fun surfaces(): List<Surface> = listOf(
        Surface("TV Home", 1920, 1080, television = true, home = tvHome,
            copy = listOf("Home", "Movies", "My List", "The End of Oak Street", "2026", "Action, Adventure", "1h 40m", "The Platt family bands together"),
            hero = listOf("2026", "Action, Adventure", "1h 40m", "The Platt family bands together")),
        Surface("phone Home", 393, 852, television = false, home = home,
            copy = listOf("The End of Oak Street", "2026", "Action, Adventure", "1h 40m"),
            hero = listOf("The End of Oak Street", "2026", "Action, Adventure", "1h 40m")),
        Surface("tablet Home", 1024, 900, television = false, home = home, tablet = true,
            copy = listOf("The End of Oak Street", "2026", "Action, Adventure", "1h 40m"),
            hero = listOf("The End of Oak Street")),
        Surface("desktop Home", 1440, 900, television = false, home = home, desktop = true,
            copy = listOf("The End of Oak Street", "2026", "Action, Adventure", "1h 40m", "The Platt family bands together"),
            hero = listOf("2026", "Action, Adventure", "1h 40m", "The Platt family bands together")),
    )

    @Test fun textOverArtworkKeepsFourAndAHalfToOne() = ui {
        withWhiteArtwork {
            for (surface in surfaces()) {
                Shot(surface).use { shot ->
                    shot.until("${surface.name} to compose its copy over the artwork") { shot.textNodes().isNotEmpty() }
                    // Reading before the entry fades and focus fills finish samples the blend, not the ink.
                    shot.frames(SettleFrames)
                    val overArtwork = shot.copyOverArtwork()
                    val drawn = shot.textNodes().joinToString("\n") { it.config.getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { text -> text.text } }
                    for (line in surface.copy) {
                        assertTrue(drawn.contains(line), "${surface.name} draws \"$line\": $drawn")
                    }
                    val overArtworkCopy = overArtwork.joinToString("\n") { it.text }
                    for (line in surface.hero) {
                        assertTrue(overArtworkCopy.contains(line), "${surface.name} keeps \"$line\" over its artwork: $overArtworkCopy")
                    }
                    for (line in overArtwork) {
                        assertTrue(line.contrast >= 4.5, "${surface.name} draws \"${line.text}\" at " +
                            "${"%.2f".format(line.contrast)}:1 (${"#%06X".format(line.ink and 0xFFFFFF)} on " +
                            "${"#%06X".format(line.background and 0xFFFFFF)})")
                    }
                    println("CONTRAST ${surface.name}: ${overArtwork.size} lines over artwork, worst ${"%.2f".format(overArtwork.minOf { it.contrast })}:1")
                }
            }
        }
    }

    private class Measurement(val text: String, val ink: Int, val background: Int, val contrast: Double)

    private inner class Shot(private val surface: Surface) : AutoCloseable {
        private var frame = 0L

        private val scene = ImageComposeScene(surface.width, surface.height, Density(1f)) {
            ViptvTheme(surface.television, pointer = !surface.television) {
                CompositionLocalProvider(LocalHomeGenres provides HomeGenrePicker(PreviewData.genres, {})) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) {
                        HomeScreen(surface.home, television = surface.television, open = {}, retry = {}, navigate = {},
                            tablet = surface.tablet, desktopChrome = surface.desktop, profile = PreviewData.profile, heroRotates = false)
                    }
                }
            }
        }

        fun until(what: String, condition: () -> Boolean) { frame = scene.renderUntil(from = frame, what = what, condition = condition) }

        fun frames(count: Int) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }

        fun copyOverArtwork(): List<Measurement> {
            val pixels = render()
            val overArtwork = textNodes().mapNotNull { measure(it, pixels) }.filter { isArtwork(it.background) }
            if (System.getenv("VIPTV_CONTRAST_TRACE") == "1") overArtwork.sortedBy { it.text }.forEach { line ->
                println("  ${surface.name} \"${line.text.take(48)}\" ${"%.2f".format(line.contrast)}:1 " +
                    "ink=${"#%06X".format(line.ink and 0xFFFFFF)} bg=${"#%06X".format(line.background and 0xFFFFFF)}")
            }
            return overArtwork
        }

        private fun render(): Bitmap = scene.render(frame * 16_666_667L).use { Bitmap.makeFromImage(it) }

        fun textNodes(): List<SemanticsNode> = allNodes()
            .filter { it.config.contains(SemanticsProperties.Text) && it.children.isEmpty() }

        private fun allNodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }

        private fun measure(node: SemanticsNode, pixels: Bitmap): Measurement? {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.trim().orEmpty()
            if (text.isEmpty()) return null
            val box = node.boundsInRoot.intersect(Rect(0f, 0f, pixels.width.toFloat(), pixels.height.toFloat()))
            if (box.width < 2f || box.height < 2f) return null
            val ring = RingPixels
            val around = ArrayList<Int>()
            for (y in (box.top.toInt() - ring)..(box.bottom.toInt() + ring)) for (x in (box.left.toInt() - ring)..(box.right.toInt() + ring)) {
                if (x < 0 || y < 0 || x >= pixels.width || y >= pixels.height) continue
                if (x in box.left.toInt() until box.right.toInt() && y in box.top.toInt() until box.bottom.toInt()) continue
                around += pixels.getColor(x, y)
            }
            if (around.isEmpty()) return null
            // A 1 px badge border and the glyphs themselves can reach into the ring: its median is the scrim behind them.
            val background = around.sortedBy(::luminance)[around.size / 2]
            var ink = background
            var inkContrast = 1.0
            for (y in box.top.toInt() until box.bottom.toInt()) for (x in box.left.toInt() until box.right.toInt()) {
                val colour = pixels.getColor(x, y)
                val value = contrast(colour, background)
                if (value > inkContrast) { inkContrast = value; ink = colour }
            }
            return Measurement(text, ink, background, inkContrast)
        }

        override fun close() = scene.close()
    }

    private suspend fun withWhiteArtwork(block: suspend () -> Unit) {
        val context = PlatformContext.INSTANCE
        val previous = runCatching { SingletonImageLoader.get(context) }.getOrNull()
        val loader = ImageLoader.Builder(context)
            .coroutineContext(Dispatchers.Unconfined).fetcherCoroutineContext(Dispatchers.Unconfined).decoderCoroutineContext(Dispatchers.Unconfined)
            .components { add(Interceptor { chain ->
                SuccessResult(Bitmap().apply { allocN32Pixels(64, 36); erase(White) }.asImage(), chain.request, DataSource.MEMORY)
            }) }
            .build()
        SingletonImageLoader.setUnsafe(loader)
        try { block() } finally {
            loader.shutdown()
            if (previous == null) SingletonImageLoader.reset() else SingletonImageLoader.setUnsafe(previous)
        }
    }

    private fun isArtwork(colour: Int) = luminance(colour) > GroundLuminance + 0.005

    private companion object {
        const val White = 0xFFFFFFFF.toInt()
        const val RingPixels = 3
        const val SettleFrames = 30
        val GroundLuminance = luminance(Palette.background.toArgb())
    }
}

private fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val value = ((argb shr shift) and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private fun contrast(a: Int, b: Int): Double {
    val first = luminance(a)
    val second = luminance(b)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}