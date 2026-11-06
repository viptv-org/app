@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.features.details.TvDetailsScreen
import org.viptv.app.features.home.HomeScreen
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveClock
import org.viptv.app.features.live.LivePreview
import org.viptv.app.features.live.LiveProgram
import org.viptv.app.features.live.LocalLiveClock
import org.viptv.app.features.live.TvLivePreview
import org.viptv.app.preview.PreviewData
import org.viptv.app.preview.PreviewTvDetails
import org.viptv.app.preview.PreviewTvHome
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.CardMetadata
import org.viptv.engine.wire.MediaIdentity

// Every line drawn over artwork or over a component's own fill keeps 4.5:1, so the fixture behind the copy is flat white.
class TextOverArtworkContrastTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val white = PreviewData.hero.copy(landscape = WhiteArtwork, poster = null, logo = null,
        metadata = CardMetadata(year = 2026, runtime_minutes = 100, genres = listOf("Action", "Adventure"), rating = 7.7, certification = "PG-13",
            cast = listOf("Lena Ortiz", "Marcus Reid", "Piper Chan")))
    private val home = PreviewData.home.copy(hero = white, featured = emptyList())
    private val tvHome = PreviewTvHome.home.copy(hero = white, featured = emptyList())
    private val detail = PreviewData.detail.copy(item = white)
    private val series = PreviewTvDetails.series.copy(item = white.copy(identity = MediaIdentity("detail-series", "series", emptyList())))
    private val metadata = listOf("2026", "1h 40m", "Action / Adventure", "★ 7.7")
    private val starring = "Starring Lena Ortiz, Marcus Reid, Piper Chan"
    private val desktopMetadata = listOf("Movie", "2026", "1h 40m", "Action / Adventure", "★ 7.7")
    private val description = "The Platt family bands together"
    private val program = LiveProgram("p1", "Evening News", 0, 30, description = "The evening bulletin", artwork = WhiteArtwork)
    private val channel = LiveChannel(Card(MediaIdentity("live-white", "channel", emptyList()), provider_id = "preview", title = "BBC One HD",
        landscape = WhiteArtwork, poster = null, badges = listOf("1080p")), number = "101", category = "News", programs = listOf(program))

    private class Surface(val name: String, val width: Int, val height: Int, val television: Boolean,
        val content: @Composable () -> Unit, val copy: List<String>, val hero: List<String>)

    private fun surfaces(): List<Surface> = listOf(
        Surface("TV Home", 1920, 1080, television = true,
            content = { HomeScreen(tvHome, television = true, open = {}, retry = {}, navigate = {}, profile = PreviewData.profile) },
            copy = listOf("Home", "Movies", "My List", "The End of Oak Street") + metadata + description,
            hero = metadata + description),
        Surface("phone Home", 393, 852, television = false,
            content = { HomeScreen(home, television = false, open = {}, retry = {}) },
            copy = listOf("The End of Oak Street") + metadata, hero = metadata),
        Surface("tablet Home", 1024, 900, television = false,
            content = { HomeScreen(home, television = false, tablet = true, open = {}, retry = {}, navigate = {}) },
            copy = listOf("The End of Oak Street") + metadata, hero = listOf("The End of Oak Street")),
        Surface("desktop Home", 1440, 900, television = false,
            content = { HomeScreen(home, television = false, desktopChrome = true, open = {}, retry = {}) },
            copy = listOf("The End of Oak Street") + metadata + description, hero = metadata + description),
        Surface("TV Details", 1920, 1080, television = true,
            content = { TvDetailsScreen(series, busy = false, error = null, sources = {}, extras = PreviewTvDetails.seriesExtras) },
            copy = listOf("The End of Oak Street", starring) + metadata + description,
            hero = metadata + description + starring),
        Surface("phone Details", 393, 852, television = false,
            content = { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}) },
            copy = listOf("The End of Oak Street", "Movie") + metadata + description + starring, hero = emptyList()),
        Surface("tablet Details", 1024, 900, television = false,
            content = { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, tablet = true) },
            copy = listOf("The End of Oak Street", "Movie") + metadata + description + starring,
            hero = listOf("The End of Oak Street", "Movie") + metadata + description + starring),
        Surface("desktop Details", 1440, 900, television = false,
            content = { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}) },
            copy = listOf("The End of Oak Street") + desktopMetadata + description + starring,
            hero = listOf("The End of Oak Street") + desktopMetadata + description + starring),
        Surface("browser Details", 1280, 800, television = false,
            content = { DetailsScreen(detail, busy = false, error = null, back = {}, sources = {}, browser = true) },
            copy = listOf("The End of Oak Street", "Add to My List", starring), hero = listOf("The End of Oak Street")),
        Surface("phone Live", 393, 852, television = false,
            content = { LivePreview(channel, program, now = 15, phone = true, television = false, scale = 1f, watch = {}, favourite = {}) },
            copy = listOf("LIVE", "101 · BBC One HD", "Evening News", "15 min left"),
            hero = listOf("LIVE", "101 · BBC One HD", "Evening News")),
        Surface("TV Live", 1920, 1080, television = true,
            content = { TvLivePreview(channel, program, now = 15, scale = 1f, favorite = null, watch = {}) },
            copy = listOf("LIVE", "101 · BBC One HD", "Evening News", "15 min left", "OK to watch"),
            hero = listOf("OK to watch")),
    )

    @Test fun textOverArtworkKeepsFourAndAHalfToOne() = ui {
        withWhiteArtwork {
            val missing = mutableListOf<String>()
            val low = mutableListOf<String>()
            for (surface in surfaces()) {
                Shot(surface).use { shot ->
                    shot.until("${surface.name} to compose its copy over the artwork") { shot.textNodes().isNotEmpty() }
                    // Reading before the entry fades and focus fills finish samples the blend, not the ink.
                    shot.frames(SettleFrames)
                    val overArtwork = shot.copyOverArtwork()
                    val drawn = shot.textNodes().joinToString("\n") { it.config.getOrNull(SemanticsProperties.Text)!!.joinToString(" ") { text -> text.text } }
                    surface.copy.filterNot(drawn::contains).forEach { missing += "${surface.name} does not draw \"$it\"" }
                    val overArtworkCopy = overArtwork.joinToString("\n") { it.text }
                    (surface.hero.filterNot(overArtworkCopy::contains)).forEach { missing += "${surface.name} does not keep \"$it\" over its artwork" }
                    overArtwork.filter { it.contrast < 4.5 }.forEach { line ->
                        low += "${surface.name} draws \"${line.text}\" at ${"%.2f".format(line.contrast)}:1 " +
                            "(${"#%06X".format(line.ink and 0xFFFFFF)} on ${"#%06X".format(line.background and 0xFFFFFF)})"
                    }
                    println("CONTRAST ${surface.name}: ${overArtwork.size} lines over artwork, worst ${"%.2f".format(overArtwork.minOf { it.contrast })}:1")
                }
            }
            assertTrue(missing.isEmpty(), missing.joinToString("\n"))
            assertTrue(low.isEmpty(), low.joinToString("\n"))
        }
    }

    private class Measurement(val text: String, val ink: Int, val background: Int, val contrast: Double)

    private inner class Shot(private val surface: Surface) : AutoCloseable {
        private var frame = 0L

        private val scene = ImageComposeScene(surface.width, surface.height, Density(1f)) {
            ViptvTheme(surface.television, pointer = !surface.television) {
                CompositionLocalProvider(LocalHomeGenres provides HomeGenrePicker(PreviewData.genres, {}),
                    LocalLiveClock provides LiveClock(originMillis = 0, nowMillis = 0)) {
                    Box(Modifier.fillMaxSize().background(Palette.background)) { surface.content() }
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
            var drawn = background
            var drawnContrast = 1.0
            for (y in box.top.toInt() until box.bottom.toInt()) for (x in box.left.toInt() until box.right.toInt()) {
                val colour = pixels.getColor(x, y)
                val value = contrast(colour, background)
                if (value > drawnContrast) { drawnContrast = value; drawn = colour }
            }
            // A glyph a few pixels wide never reaches its own colour: compare the ink the app specifies, as long as the drawn peak is that ink.
            val ink = Inks.minByOrNull { distance(it, drawn) }?.takeIf { distance(it, drawn) <= InkTolerance } ?: drawn
            return Measurement(text, ink, background, contrast(ink, background))
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
        const val WhiteArtwork = "https://artwork.test/white.jpg"
        const val RingPixels = 3
        const val SettleFrames = 30
        val GroundLuminance = luminance(Palette.background.toArgb())
        val Inks = listOf(Palette.text, Palette.body, Palette.secondary, Palette.tertiary, Palette.background, Palette.danger)
            .map(Color::toArgb)
        const val InkTolerance = 24
    }
}

private fun luminance(argb: Int): Double {
    fun channel(shift: Int): Double {
        val value = ((argb shr shift) and 0xFF) / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

private fun distance(first: Int, second: Int): Int {
    fun channel(shift: Int) = kotlin.math.abs(((first shr shift) and 0xFF) - ((second shr shift) and 0xFF))
    return maxOf(channel(16), maxOf(channel(8), channel(0)))
}

private fun contrast(a: Int, b: Int): Double {
    val first = luminance(a)
    val second = luminance(b)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}