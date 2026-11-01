package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.features.live.LiveEmptyArtwork
import org.viptv.app.features.live.LiveEmptyTallArt
import org.viptv.app.features.live.LiveEmptyWideArt
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LiveEmptyArtworkTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun thePhotosArePackagedNotPlaceholders() = runBlocking {
        assertTrue(Res.readBytes(LiveEmptyWideArt).size > 50_000, "the wide photo is a real packaged image")
        assertTrue(Res.readBytes(LiveEmptyTallArt).size > 50_000, "the tall photo is a real packaged image")
    }

    @Test fun theWidePhotoRendersBehindItsLeftScrim() {
        val scene = scene(1920, 948, wide = true)
        try {
            val pixels = render(scene, "live-empty-wide")
            assertTrue(distinct(pixels) > 400, "a full scene, not a flat placeholder: ${distinct(pixels)} colours")
            assertTrue(luminance(pixels, 1920, 12, 474) < 0.2f, "the left scrim keeps the copy arm dark")
        } finally { scene.close() }
    }

    @Test fun theTallPhotoRendersAtPhoneWidthBehindItsBottomScrim() {
        val scene = scene(390, 700, wide = false)
        try {
            val pixels = render(scene, "live-empty-tall")
            assertTrue(distinct(pixels) > 400, "a full scene at phone width: ${distinct(pixels)} colours")
            assertTrue(luminance(pixels, 390, 195, 686) < 0.2f, "the bottom scrim carries the copy")
        } finally { scene.close() }
    }

    @Test fun theTwoScenesDifferSoTheLayoutPicksTheRightFile() {
        val wide = scene(1920, 948, wide = true)
        val tall = scene(1920, 948, wide = false)
        try {
            val widePixels = render(wide, "live-empty-wide-at-tv")
            val tallPixels = render(tall, "live-empty-tall-at-tv")
            assertTrue(widePixels.indices.count { widePixels[it] != tallPixels[it] } > widePixels.size / 10,
                "the wide and tall files are different compositions")
        } finally { wide.close(); tall.close() }
    }

    private fun scene(width: Int, height: Int, wide: Boolean) = ImageComposeScene(width, height, Density(1f)) {
        ViptvTheme(false) {
            Surface(Modifier.fillMaxSize(), color = Palette.background) { LiveEmptyArtwork(wide, Modifier.fillMaxSize()) }
        }
    }

    private fun render(scene: ImageComposeScene, name: String): IntArray {
        repeat(20) { scene.render(it * 16_666_667L).close(); Thread.sleep(3) }
        return scene.render(1_000_000_000L).use { image ->
            System.getenv("VIPTV_LIVE_CAPTURE_DIR")?.let { path ->
                File(path).mkdirs()
                image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) }
            }
            val width = image.width
            Bitmap.makeFromImage(image).use { bitmap ->
                IntArray(width * image.height) { index -> bitmap.getColor(index % width, index / width) }
            }
        }
    }

    private fun distinct(pixels: IntArray) = pixels.toSet().size

    private fun channel(colour: Int) = ((colour shr 16) and 255) / 255f

    private fun luminance(pixels: IntArray, width: Int, x: Int, y: Int) = channel(pixels[y * width + x])
}