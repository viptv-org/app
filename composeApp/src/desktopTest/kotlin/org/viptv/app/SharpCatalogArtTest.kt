package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import io.ktor.http.Url
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.browse.BrowseScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class SharpCatalogArtTest {
    @get:Rule val uiThread = UiThreadRule()

    private val poster = "https://simkl.in/posters/20/204275190b49d24215_m.webp"
    private val anime = Card(MediaIdentity("simkl:anime:1", "series", emptyList()), category = "anime",
        provider_id = "simkl", title = "Anime", badges = listOf("Simkl"), poster = poster)

    @Test fun phoneAnimeGridRequestsPostersAtTheCardsPixelSize() = runBlocking {
        val cardPx = ((PHONE_DP - 2 * 16 - 2 * 10) / 3f * DENSITY).toInt()
        val requests = render { BrowseScreen(Screen.Anime, null, false, open = {}, loadedCards = listOf(anime)) }
        val (url, decode) = requests.entries.single { it.key.contains("simkl.in") }.toPair()
        val sized = Url(url)
        assertEquals("wsrv.nl", sized.host, url)
        assertEquals(poster, sized.parameters["url"], url)
        val w = assertNotNull(sized.parameters["w"]?.toInt(), url)
        val h = assertNotNull(sized.parameters["h"]?.toInt(), url)
        assertTrue(w >= cardPx, "image service width $w is below the card's $cardPx px: $url")
        assertTrue(h >= cardPx * 3 / 2, "image service height $h is below the card's ${cardPx * 3 / 2} px: $url")
        assertTrue(decode >= cardPx, "Coil decodes at $decode px for a $cardPx px card")
    }

    private suspend fun render(content: @androidx.compose.runtime.Composable () -> Unit): Map<String, Int> {
        val requested = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val context = PlatformContext.INSTANCE
        val previous = SingletonImageLoader.get(context)
        val loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                requested[chain.request.data.toString()] = (chain.size.width as? Dimension.Pixels)?.px ?: -1
                SuccessResult(Bitmap().apply { allocN32Pixels(8, 12) }.asImage(), chain.request, DataSource.MEMORY) as ImageResult
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            val scene = ImageComposeScene((PHONE_DP * DENSITY).toInt(), (852 * DENSITY).toInt(), Density(DENSITY)) {
                ViptvTheme(true) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
            }
            try { repeat(10) { scene.render(it * 16_666_667L).close(); delay(10) } } finally { scene.close() }
        } finally { loader.shutdown(); SingletonImageLoader.setUnsafe(previous) }
        return requested
    }

    private companion object {
        const val PHONE_DP = 393
        const val DENSITY = 3f
    }
}
