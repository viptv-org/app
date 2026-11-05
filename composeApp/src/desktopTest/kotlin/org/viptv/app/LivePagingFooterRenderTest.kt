package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Rule
import org.viptv.app.features.live.LiveChannel
import org.viptv.app.features.live.LiveScreen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class LivePagingFooterRenderTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private data class Case(val name: String, val width: Int, val height: Int, val television: Boolean)

    private val cases = listOf(
        Case("phone-390", 390, 844, false),
        Case("desktop-1280", 1280, 800, false),
        Case("tv-1920", 1920, 1080, true),
    )

    private val channels = listOf(
        LiveChannel(Card(MediaIdentity("one", "live", emptyList()), provider_id = "m3u:1", title = "LAT | Discovery Channel HD", badges = emptyList())),
        LiveChannel(Card(MediaIdentity("two", "live", emptyList()), provider_id = "m3u:1", title = "ESPN HD", badges = emptyList())),
        LiveChannel(Card(MediaIdentity("three", "live", emptyList()), provider_id = "m3u:1", title = "Movie Night", badges = emptyList())),
    )

    @Composable
    private fun Host(television: Boolean, busy: Boolean, more: (() -> Unit)?, pageError: String?) {
        ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            LiveScreen(channels, television, start = null, now = null, watch = {},
                more = more, busy = busy, pageError = pageError)
        } }
    }

    @Test fun render() = runBlocking {
        val dir = System.getenv("VIPTV_773_RENDER_DIR")?.let(::File) ?: return@runBlocking
        dir.mkdirs()

        suspend fun shot(name: String, case: Case, content: @Composable () -> Unit) {
            val scene = ImageComposeScene(case.width, case.height, Density(1f)) { content() }
            try {
                var frame = 0L
                repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
                delay(250)
                repeat(12) { scene.render(frame++ * 16_666_667L).close(); yield() }
                scene.render(frame++ * 16_666_667L).use { image ->
                    image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) }
                }
            } finally { scene.close() }
        }

        for (case in cases) {
            shot("${case.name}-loading", case) { Host(case.television, busy = true, more = {}, pageError = null) }
            shot("${case.name}-ended", case) { Host(case.television, busy = false, more = null, pageError = null) }
            shot("${case.name}-failed", case) { Host(case.television, busy = false, more = {}, pageError = "oops") }
        }
    }
}
