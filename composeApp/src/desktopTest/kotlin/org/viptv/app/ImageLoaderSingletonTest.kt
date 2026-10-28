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
import org.viptv.app.preview.UiPreview
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

/**
 * app#134: Coil builds its default singleton image loader the first time any
 * `AsyncImage` composes, and `SingletonImageLoader.setSafe` then refuses a
 * factory for the rest of the JVM. A desktop test JVM renders cards long before
 * it renders a preview, so composing [UiPreview] must install the app's loader
 * over Coil's default instead of throwing "The singleton image loader has
 * already been created", and it must keep exactly one loader afterwards.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ImageLoaderSingletonTest {
    @get:Rule val uiThread = UiThreadRule()

    /** Public artwork, the shape [org.viptv.app.images.PublicArtworkMapper] proxies. */
    private val poster = "https://simkl.in/posters/20/204275190b49d24215_m.webp"
    private val card = Card(MediaIdentity("simkl:134:1", "movie", emptyList()), provider_id = "simkl",
        title = "Square One", poster = poster, badges = emptyList())

    @Test fun previewInstallsTheSharedImageLoaderAfterACardCreatedCoilsDefault() = runBlocking {
        // Coil's default loader is what any card leaves behind in a test JVM.
        SingletonImageLoader.reset()

        // 1. A card with artwork, as an earlier UI test in this JVM renders one.
        render { MediaCard(card, width = 240, open = {}) }

        // 2. The preview installs the app's loader over Coil's default.
        render(television = true) { UiPreview(television = true, screen = Screen.Search) }
        val installed = SingletonImageLoader.get(PlatformContext.INSTANCE)
        val mapped = installed.components.map(poster, Options(PlatformContext.INSTANCE)).toString()
        assertTrue(mapped.startsWith("https://wsrv.nl/"), "the installed loader maps public artwork, was $mapped")

        // 3. Screens and previews that compose afterwards reuse that loader.
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