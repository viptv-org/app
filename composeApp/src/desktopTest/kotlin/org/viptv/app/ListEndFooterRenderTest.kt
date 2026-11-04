package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.viptv.app.components.ListEndFooter
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class ListEndFooterRenderTest {
    @Composable
    private fun FooterView(loading: Boolean = false, ended: Boolean = false, failed: Boolean = false,
        television: Boolean = false, scale: Float = 1f) {
        ViptvTheme(television) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) {
                Box(Modifier.fillMaxWidth().padding(horizontal = if (television) 96.dp * scale else 24.dp),
                    contentAlignment = Alignment.Center) {
                    ListEndFooter(loading = loading, ended = ended, failed = failed, retry = {},
                        modifier = Modifier.fillMaxWidth(), television = television, scale = scale)
                }
            }
        }
    }

    @Test fun render() = runBlocking {
        val dir = System.getenv("VIPTV_712_RENDER_DIR")?.let(::File) ?: return@runBlocking
        dir.mkdirs()

        suspend fun shot(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
            val scene = ImageComposeScene(width, height, Density(1f)) { content() }
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

        shot("phone-loading", 390, 844) { FooterView(loading = true) }
        shot("phone-ended", 390, 844) { FooterView(ended = true) }
        shot("phone-failed", 390, 844) { FooterView(failed = true) }
        shot("tv-loading", 1920, 1080) { FooterView(loading = true, television = true, scale = 1f) }
        shot("tv-ended", 1920, 1080) { FooterView(ended = true, television = true, scale = 1f) }
        shot("tv-failed", 1920, 1080) { FooterView(failed = true, television = true, scale = 1f) }
    }
}