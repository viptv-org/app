package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
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
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.images.ConfigureImageLoading
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LibraryContinueArtworkTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun myListRequestsAndDrawsEachCardsPoster() = runBlocking {
        withFakeArtwork { art ->
            val cards = (1..4).map { art.card("Saved $it", poster = art.url("poster-$it"), landscape = null) }
            val shot = render { LibraryScreen(cards, available = true, television = false, open = {}, history = {}, home = {}) }
            for (index in 1..4) {
                assertTrue(art.paths.containsKey("/poster-$index.png"), "My List asked for card $index's poster: ${art.paths.keys}")
            }
            assertTrue(shot.count(PosterInk) > 300, "My List drew the posters instead of empty tiles")
        }
    }

    @Test fun continueWatchingRequestsAndDrawsItsArtwork() = runBlocking {
        withFakeArtwork { art ->
            val cards = listOf(
                art.card("Has a still", poster = art.url("poster-still"), landscape = art.url("still")),
                art.card("Poster only", poster = art.url("poster-only"), landscape = null),
            )
            val shot = render {
                ContinueWatchingScreen(ContinueWatchingPage(cards, loaded = true), play = {}, open = {},
                    more = null, retry = {}, back = {}, home = {})
            }
            assertTrue(art.paths.containsKey("/still.png"), "a card with a still asks for it: ${art.paths.keys}")
            assertTrue(art.paths.containsKey("/poster-only.png"), "a card with only a poster asks for that: ${art.paths.keys}")
            assertTrue(shot.count(StillInk) > 300, "the still is drawn in the 16:9 slot")
            assertTrue(shot.count(PosterInk) > 300, "the poster fills its 16:9 slot instead of an empty tile")
        }
    }

    @Test fun aPosterThatFailsOnceIsRetriedAndThenDrawn() = runBlocking {
        withFakeArtwork(failFirst = 1) { art ->
            val cards = listOf(art.card("Retried", poster = art.url("poster-retry"), landscape = null))
            val shot = render(frames = 260) { LibraryScreen(cards, available = true, television = false, open = {}, history = {}, home = {}) }
            assertTrue((art.paths["/poster-retry.png"] ?: 0) > 1, "a failed poster is asked again: ${art.paths}")
            assertTrue(shot.count(PosterInk) > 300, "the retried poster is drawn instead of the no-art tile")
        }
    }

    @Test fun aPosterThatKeepsFailingSettlesOnTheNoArtTile() = runBlocking {
        withFakeArtwork(failFirst = 99) { art ->
            val cards = listOf(art.card("Broken", poster = art.url("poster-broken"), landscape = null))
            val shot = render(frames = 320) { LibraryScreen(cards, available = true, television = false, open = {}, history = {}, home = {}) }
            assertEquals(ART_ATTEMPTS + 1, art.paths["/poster-broken.png"] ?: 0, "one request and $ART_ATTEMPTS retries: ${art.paths}")
            assertEquals(0, shot.count(PosterInk), "a poster that never loads leaves the no-art tile")
        }
    }

    private class Artwork(val origin: String) {
        val paths = ConcurrentHashMap<String, Int>()

        fun url(name: String) = "$origin/$name.png"

        fun card(title: String, poster: String?, landscape: String?) = Card(
            MediaIdentity(title.lowercase().replace(' ', '-'), "series", emptyList()), provider_id = "library",
            title = title, badges = emptyList(), poster = poster, landscape = landscape)
    }

    private suspend fun withFakeArtwork(failFirst: Int = 0, block: suspend (Artwork) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val art = Artwork("http://127.0.0.1:${server.address.port}")
        server.createContext("/") { exchange ->
            val name = exchange.requestURI.path
            val served = (art.paths[name] ?: 0) + 1
            art.paths[name] = served
            if (served <= failFirst) {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
                return@createContext
            }
            val body = png(if (name.startsWith("/still")) StillInk else PosterInk)
            exchange.responseHeaders.add("content-type", "image/png")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try { block(art) } finally { server.stop(0) }
    }

    private suspend fun render(frames: Int = 120, content: @Composable () -> Unit): Shown {
        val context = PlatformContext.INSTANCE
        val previous: ImageLoader? = runCatching { SingletonImageLoader.get(context) }.getOrNull()
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            ViptvTheme(true) {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    ConfigureImageLoading()
                    content()
                }
            }
        }
        try {
            var shown: Shown? = null
            repeat(frames) { frame ->
                scene.render(frame * 16_666_667L).use { image -> shown = Shown(Bitmap.makeFromImage(image)) }
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
        const val WIDTH = 900
        const val HEIGHT = 700
        val PosterInk = 0xFFE01020.toInt()
        val StillInk = 0xFF10D040.toInt()

        fun png(colour: Int): ByteArray {
            val image = BufferedImage(24, 36, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until image.height) for (x in 0 until image.width) image.setRGB(x, y, colour)
            return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        }
    }
}
