package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.ART_ATTEMPTS
import org.viptv.app.features.details.TvDetailBackdrop
import org.viptv.app.features.details.TvEpisodeCard
import org.viptv.app.images.ConfigureImageLoading
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvDetailsArtworkRetryTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun anEpisodeThumbnailThatFailsOnceIsRetriedAndThenDrawn() = runBlocking {
        withFakeArtwork(failFirst = mapOf("/episode.png" to 1)) { art ->
            val card = art.card(title = "Episode One", landscape = art.url("episode"))
            val shot = render(520, 420, frames = 260, until = { it.count(StillInk) > 300 }) { TvEpisodeCard(card, 1f, {}, {}, null, FocusRequester()) }
            assertTrue((art.paths["/episode.png"] ?: 0) > 1, "a failed thumbnail is asked again: ${art.paths}")
            assertTrue(shot.count(StillInk) > 300, "the retried thumbnail is drawn instead of the title text")
        }
    }

    @Test fun anEpisodeThumbnailThatKeepsFailingSettlesOnTheTitleText() = runBlocking {
        withFakeArtwork(failFirst = mapOf("/episode.png" to 99)) { art ->
            val card = art.card(title = "Episode One", landscape = art.url("episode"))
            val shot = render(520, 420, frames = 320) { TvEpisodeCard(card, 1f, {}, {}, null, FocusRequester()) }
            assertEquals(ART_ATTEMPTS + 1, art.paths["/episode.png"] ?: 0, "one request and $ART_ATTEMPTS retries: ${art.paths}")
            assertEquals(0, shot.count(StillInk), "a thumbnail that never loads leaves the title text")
        }
    }

    @Test fun aCancelledBackdropRequestIsRetriedInsteadOfFallingBackToThePoster() = runBlocking {
        withFakeArtwork(failFirst = mapOf("/landscape.png" to 1)) { art ->
            val card = art.card(title = "Show", landscape = art.url("landscape"), poster = art.url("poster"))
            render(400, 400, frames = 260) { TvDetailBackdrop(card, 1f) { 0f } }
            assertTrue((art.paths["/landscape.png"] ?: 0) > 1, "the backdrop is asked again: ${art.paths}")
            assertNull(art.paths["/poster.png"], "a retried landscape never falls back to the poster: ${art.paths}")
        }
    }

    @Test fun aBackdropThatKeepsFailingFallsBackToThePosterAfterItsRetries() = runBlocking {
        withFakeArtwork(failFirst = mapOf("/landscape.png" to 99)) { art ->
            val card = art.card(title = "Show", landscape = art.url("landscape"), poster = art.url("poster"))
            render(400, 400, frames = 320) { TvDetailBackdrop(card, 1f) { 0f } }
            assertEquals(ART_ATTEMPTS, art.paths["/landscape.png"] ?: 0, "the landscape is retried before the fallback: ${art.paths}")
            assertEquals(1, art.paths["/poster.png"] ?: 0, "the poster is the fallback: ${art.paths}")
        }
    }

    private class Artwork(val origin: String) {
        private val run = UUID.randomUUID()
        val paths = ConcurrentHashMap<String, Int>()

        // Unique per server, so Coil's on-disk cache never answers in place of the server.
        fun url(name: String) = "$origin/$name.png?run=$run"

        fun card(title: String, landscape: String?, poster: String? = null) = Card(
            MediaIdentity(title.lowercase().replace(' ', '-'), "series", emptyList()), provider_id = "library",
            title = title, badges = emptyList(), poster = poster, landscape = landscape)
    }

    private suspend fun withFakeArtwork(failFirst: Map<String, Int> = emptyMap(), block: suspend (Artwork) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val art = Artwork("http://127.0.0.1:${server.address.port}")
        server.createContext("/") { exchange ->
            val name = exchange.requestURI.path
            val served = (art.paths[name] ?: 0) + 1
            art.paths[name] = served
            if (served <= (failFirst[name] ?: 0)) {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
                return@createContext
            }
            val body = png(if (name.contains("poster")) PosterInk else StillInk)
            exchange.responseHeaders.add("content-type", "image/png")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try { block(art) } finally { server.stop(0) }
    }

    private suspend fun render(width: Int, height: Int, frames: Int = 120, until: (Shown) -> Boolean = { false }, content: @Composable () -> Unit): Shown {
        val context = PlatformContext.INSTANCE
        val previous: ImageLoader? = runCatching { SingletonImageLoader.get(context) }.getOrNull()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    ConfigureImageLoading()
                    content()
                }
            }
        }
        try {
            var shown: Shown? = null
            for (frame in 0 until frames) {
                val shot = scene.render(frame * 16_666_667L).use { image -> Shown(Bitmap.makeFromImage(image)) }
                shown = shot
                if (until(shot)) break
                delay(25)
            }
            return assertNotNull(shown)
        } finally {
            scene.close()
            runCatching { SingletonImageLoader.get(context).shutdown() }
            previous?.let { SingletonImageLoader.setUnsafe(it) }
        }
    }

    private class Shown(private val pixels: Bitmap) {
        fun count(colour: Int): Int {
            var found = 0
            for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
                if (pixels.getColor(x, y) == colour) found++
            }
            return found
        }
    }

    private companion object {
        val PosterInk = 0xFFE01020.toInt()
        val StillInk = 0xFF10D040.toInt()

        fun png(colour: Int): ByteArray {
            val image = BufferedImage(24, 36, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until image.height) for (x in 0 until image.width) image.setRGB(x, y, colour)
            return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
    }
}
