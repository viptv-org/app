package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import coil3.size.Dimension
import coil3.size.Size
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Image
import org.viptv.app.features.home.LandscapeArt
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.home.TvCatalogCard
import org.viptv.app.features.home.landscapeArt
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class LandscapePosterFallbackTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private val posterUrl = "https://art.example/poster/tt0903747.jpg"
    private val landscapeUrl = "https://art.example/background/tt0903747.jpg"
    private val imported = Card(MediaIdentity("tt0903747", "series", emptyList()), provider_id = "library",
        title = "Imported Show", subtitle = "S2 E4", badges = emptyList(), progress = 0.35,
        poster = posterUrl, landscape = null)

    @Test fun landscapeArtPrefersTheStillThenThePosterThenNothing() {
        assertEquals(LandscapeArt.Poster(posterUrl), imported.landscapeArt())
        assertEquals(LandscapeArt.Still(landscapeUrl), imported.copy(landscape = landscapeUrl).landscapeArt())
        assertEquals(LandscapeArt.Poster(posterUrl), imported.copy(landscape = " ").landscapeArt())
        assertEquals(LandscapeArt.None, imported.copy(poster = null).landscapeArt())
        assertEquals(LandscapeArt.None, imported.copy(poster = "").landscapeArt())
    }

    @Test fun phoneLandscapeCardWithOnlyAPosterShowsThePoster() = onEdt {
        withFakeArtwork { requested ->
            val pixel = renderCenter(PHONE_ART) { MediaCard(imported, width = PHONE_ART, open = {}) }
            assertTrue(requested.any { it.contains("poster") }, "the poster is requested: $requested")
            assertEquals(artColor(posterUrl), pixel, "the card's centre shows the poster, not the blank surface")
        }
    }

    @Test fun tvLandscapeCardWithOnlyAPosterShowsThePoster() = onEdt {
        withFakeArtwork { requested ->
            val pixel = renderCenter(TV_ART) { TvCatalogCard(imported, 1f, false, focused = {}, open = {}) }
            assertTrue(requested.any { it.contains("poster") }, "the poster is requested: $requested")
            assertEquals(artColor(posterUrl), pixel, "the card's centre shows the poster, not the title tile")
        }
    }

    @Test fun landscapeStillIsUnchangedWhenPresent() = onEdt {
        val still = imported.copy(landscape = landscapeUrl)
        withFakeArtwork { requested ->
            assertEquals(artColor(landscapeUrl), renderCenter(PHONE_ART) { MediaCard(still, width = PHONE_ART, open = {}) })
            assertEquals(artColor(landscapeUrl), renderCenter(TV_ART) { TvCatalogCard(still, 1f, false, focused = {}, open = {}) })
            assertTrue(requested.none { it.contains("poster") }, "a card with a still never loads its poster: $requested")
        }
    }

    @Test fun tvLandscapeCardWithNoImageKeepsTheTitleTile() = onEdt {
        withFakeArtwork { requested ->
            val pixel = renderCenter(TV_ART) { TvCatalogCard(imported.copy(poster = null), 1f, false, focused = {}, open = {}) }
            assertTrue(requested.isEmpty(), "nothing to load: $requested")
            assertEquals(0xFF1A1A1C.toInt(), pixel, "the title tile's dark fill")
        }
    }

    private suspend fun renderCenter(artWidth: Int, content: @Composable () -> Unit): Int {
        val width = artWidth + 80; val height = artWidth * 9 / 16 + 160
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        try {
            repeat(10) { scene.render(it * 16_666_667L).close(); delay(10) }
            scene.render(1_000_000_000L).use { image ->
                val bitmap = Bitmap.makeFromImage(image)
                return bitmap.getColor(artWidth / 2, artWidth * 9 / 16 / 2)
            }
        } finally { scene.close() }
    }

    @Test fun captureMixedContinueWatchingRows() = onEdt {
        val path = System.getenv("VIPTV_LANDSCAPE_FALLBACK_CAPTURE_DIR") ?: return@onEdt
        val row = listOf(
            imported.copy(title = "Imported Show", poster = "https://art.example/poster1.jpg"),
            imported.copy(identity = MediaIdentity("tt2", "movie", emptyList()), title = "With a still",
                subtitle = "1h 12m left", landscape = "https://art.example/history1.jpg", poster = "https://art.example/poster2.jpg", progress = .6),
            imported.copy(identity = MediaIdentity("tt3", "movie", emptyList()), title = "Imported Movie",
                subtitle = "48m left", poster = "https://art.example/poster5.jpg", progress = .2),
            imported.copy(identity = MediaIdentity("tt4", "movie", emptyList()), title = "No art at all",
                subtitle = "20m left", poster = null, progress = .8),
        )
        withPreviewArtwork {
            File(path).mkdirs()
            capture(File(path, "phone-continue-watching.png"), 600, 420) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { MediaCard(it, width = 240, open = {}) }
                        }
                    }
                }
            }
            capture(File(path, "tv-continue-watching.png"), 1700, 380) {
                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { TvCatalogCard(it, 1f, false, focused = {}, open = {}) }
                }
            }
        }
    }

    private suspend fun capture(file: File, width: Int, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme(true) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        try {
            repeat(12) { scene.render(it * 16_666_667L).close(); delay(20) }
            scene.render(2_000_000_000L).use { image -> image.encodeToData()!!.use { file.writeBytes(it.bytes) } }
        } finally { scene.close() }
    }

    private companion object {
        const val PHONE_ART = 240
        const val TV_ART = 384

        fun artColor(url: String) = (0xFF shl 24) or (url.hashCode() and 0x7F7F7F) or 0x202020

        suspend fun withFakeArtwork(block: suspend (requested: Set<String>) -> Unit) = withLoader({ url, _ ->
            Bitmap().apply { allocN32Pixels(160, 240); erase(artColor(url)) }
        }, block)

        suspend fun withPreviewArtwork(block: suspend () -> Unit) {
            val dir = File("src/commonMain/composeResources/files/ui-preview")
            withLoader({ url, size ->
                val image = Image.makeFromEncoded(File(dir, url.substringAfterLast('/')).readBytes())
                val w = (size.width as? Dimension.Pixels)?.px; val h = (size.height as? Dimension.Pixels)?.px
                if (w == null || h == null || w >= image.width) Bitmap.makeFromImage(image)
                else Bitmap().apply {
                    allocN32Pixels(w, h)
                    Canvas(this).drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
                }
            }) { block() }
        }

        suspend fun withLoader(picture: (String, Size) -> Bitmap, block: suspend (requested: Set<String>) -> Unit) {
            val requested = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val context = PlatformContext.INSTANCE
            val previous = SingletonImageLoader.get(context)
            val loader = ImageLoader.Builder(context).components {
                add(Interceptor { chain ->
                    val url = chain.request.data.toString(); requested += url
                    SuccessResult(picture(url, chain.size).asImage(), chain.request, DataSource.MEMORY) as ImageResult
                })
            }.build()
            SingletonImageLoader.setUnsafe(loader)
            try { block(requested) } finally { loader.shutdown(); SingletonImageLoader.setUnsafe(previous) }
        }
    }
}
