package org.viptv.app.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.viptv.app.connection.ProfilePreferences
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Background None has to reach PlayerView's own subtitle view and the sidecar layers bound beside it (app#683). */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Media3CaptionStyleTest {
    private fun layer() = SubtitleView(RuntimeEnvironment.getApplication())

    private fun boxPixels(view: SubtitleView, capture: String? = null, width: Int = 1200, height: Int = 600): Int {
        view.setStackedCues(listOf(Cue.Builder().setText("A caption line that needs a box").build()))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        capture?.let { name -> System.getenv("VIPTV_CAPTION_CAPTURE_DIR")?.let { dir ->
            val framed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(framed).apply { drawColor(android.graphics.Color.DKGRAY); drawBitmap(bitmap, 0f, 0f, null) }
            File(dir, "$name.png").outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        val row = IntArray(width)
        var count = 0
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            count += row.count { (it and 0xFFFFFF) == 0xFF0000 }
        }
        return count
    }

    @Test fun noneRemovesTheBoxFromEveryLayerAndBoxPutsItBack() {
        val builtIn = layer()
        val overlay = layer()
        val style = Media3CaptionStyle(ProfilePreferences(subtitle_background_color = "#B3FF0000"))
        style.attach(builtIn)
        style.attach(overlay)
        assertTrue(boxPixels(builtIn) > 0, "Box draws a box on PlayerView's subtitle view")
        assertTrue(boxPixels(overlay) > 0, "Box draws a box on the sidecar layer")
        style.background(null)
        assertEquals(0, boxPixels(builtIn), "None leaves no box on PlayerView's subtitle view")
        assertEquals(0, boxPixels(overlay), "None leaves no box on the sidecar layer")
        style.background("#B3FF0000")
        assertTrue(boxPixels(overlay) > 0, "Box restyles the sidecar layer live")
    }

    @Test fun aLayerBoundAfterNoneArrivesHasNoBoxEither() {
        val style = Media3CaptionStyle(ProfilePreferences(subtitle_background_color = "#B3FF0000"))
        style.background(null)
        val late = layer()
        style.attach(late)
        assertEquals(0, boxPixels(late))
    }

    @Test fun aStoredNoneDrawsNoBoxAfterARestart() {
        val restored = layer()
        Media3CaptionStyle(ProfilePreferences(subtitle_background_color = CAPTION_NO_BOX)).attach(restored)
        assertEquals(0, boxPixels(restored))
        val restoredBox = layer()
        Media3CaptionStyle(ProfilePreferences(subtitle_background_color = "#B3FF0000")).attach(restoredBox)
        assertTrue(boxPixels(restoredBox) > 0)
    }

    /** PR evidence only: `VIPTV_CAPTION_CAPTURE_DIR=<dir>` writes the sidecar layer with None and with Box. */
    @Test fun captureNoneAndBox() {
        System.getenv("VIPTV_CAPTION_CAPTURE_DIR") ?: return
        val view = layer()
        val style = Media3CaptionStyle(ProfilePreferences(subtitle_background_color = "#B3000000"))
        style.attach(view)
        boxPixels(view, capture = "android-caption-background-box")
        style.background(null)
        boxPixels(view, capture = "android-caption-background-none")
    }
}
