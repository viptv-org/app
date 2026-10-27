package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.viptv.app.preview.PreviewTvSettings
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import kotlin.test.*

/** Deterministic capture of the TV settings screens, for design comparison. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvSettingsVisualTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun captureSourcesSection() = runBlocking {
        for (variant in listOf("Sources")) {
            val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) { PreviewTvSettings(variant) { _: Screen -> } } }
            try {
                repeat(10) { scene.render(it * 16_666_667L).close(); delay(4) }
                System.getenv("VIPTV_SETTINGS_CAPTURE_DIR")?.let { path ->
                    File(path).mkdirs()
                    scene.render(4_000_000_000L).use { image ->
                        image.encodeToData()!!.use { File(path, "tv-settings-$variant.png").writeBytes(it.bytes) }
                    }
                }
            } finally { scene.close() }
        }
    }
}
