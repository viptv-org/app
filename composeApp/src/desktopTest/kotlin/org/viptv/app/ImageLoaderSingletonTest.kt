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
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.Options
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.features.home.MediaCard
import org.viptv.app.images.publicArtworkUrl
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

// Coil creates its default loader before any preview runs, so the preview must install the app's over it, not throw.
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ImageLoaderSingletonTest {
    @get:Rule val uiThread = UiThreadRule()

    private val poster = "https://simkl.in/posters/20/204275190b49d24215_m.webp"
    private val card = Card(MediaIdentity("simkl:134:1", "movie", emptyList()), provider_id = "simkl",
        title = "Square One", poster = poster, badges = emptyList())

    @Test fun previewInstallsTheSharedImageLoaderAfterACardCreatedCoilsDefault() = runBlocking {
        SingletonImageLoader.reset()
        assertNull(imageCacheDirectory(PlatformContext.INSTANCE), "desktop tests disable the persistent image cache")

        render { MediaCard(card, width = 240, open = {}) }

        render(television = true) { UiPreview(television = true, screen = Screen.Search) }
        val installed = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val mapped = installed.components.map(poster, Options(PlatformContext.INSTANCE)).toString()
        assertEquals(publicArtworkUrl(poster), mapped, "the installed loader maps public artwork")

        render(television = true) { UiPreview(television = true, screen = Screen.Home) }
        render { MediaCard(card, width = 240, open = {}) }
        assertSame(installed, SingletonImageLoader.get(PlatformContext.INSTANCE),
            "the desktop test JVM keeps one image loader")
    }

    private suspend fun render(television: Boolean = false, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(if (television) 1920 else 400, if (television) 1080 else 700, Density(1f)) {
            ViptvTheme(television) { Box(Modifier.fillMaxSize().background(Palette.background)) { content() } }
        }
        try { repeat(10) { scene.render(it * 16_666_667L).close(); delay(2) } } finally { scene.close() }
    }
}
