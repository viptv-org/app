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

/**
 * Media3's real parsers and SubtitleView drawing (app#43). A text line on screen
 * is a horizontal band of white glyph pixels, so overlapping cues show as fewer bands.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleRenderingTest {
    private fun fixture(name: String) = parseSidecarCues(requireNotNull(javaClass.getResourceAsStream("/subtitles/$name")).readBytes())

    private fun renderedLines(cues: List<Cue>, width: Int = 2400, height: Int = 1080,
        preferences: ProfilePreferences = ProfilePreferences(), scale: Float = 1f, capture: String? = null, stack: Boolean = true): Int =
        glyphRows(cues, width, height, preferences, scale, capture, stack).bands

    private class GlyphRows(val bands: Int, val lowest: Int)

    private fun glyphRows(cues: List<Cue>, width: Int = 2400, height: Int = 1080,
        preferences: ProfilePreferences = ProfilePreferences(), scale: Float = 1f, capture: String? = null, stack: Boolean = true): GlyphRows {
        val view = SubtitleView(RuntimeEnvironment.getApplication())
        view.profileStyle(preferences, scale)
        if (stack) view.showCues(cues) else view.setCues(cues)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        capture?.let { name -> System.getenv("VIPTV_SUBTITLE_CAPTURE_DIR")?.let { dir ->
            val framed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(framed).apply { drawColor(android.graphics.Color.DKGRAY); drawBitmap(bitmap, 0f, 0f, null) }
            File(dir, "$name.png").outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } }
        val row = IntArray(width)
        var bands = 0; var inside = false; var gap = 0; var lowest = -1
        for (y in 0 until height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            // White glyph pixels only: a profile background box joins the lines.
            if (row.any { it ushr 24 > 0x80 && it shr 16 and 0xFF > 0xC0 }) { if (!inside) bands++; inside = true; gap = 0; lowest = y }
            else if (inside && ++gap > 3) inside = false
        }
        return GlyphRows(bands, lowest)
    }

    @Test fun twoLineCueDrawsBothLines() {
        assertEquals(2, renderedLines(fixture("multi-line-dialogue.srt").cuesAt(2_000_000)))
        assertEquals(2, renderedLines(fixture("multi-line-dialogue.vtt").cuesAt(2_000_000)))
    }

    /** WebVTT's auto line (-1) put the last line on the screen's bottom edge, under a phone's gesture bar. */
    @Test fun webVttCueKeepsTheSameBottomMarginAsSubRip() {
        val subRip = glyphRows(fixture("multi-line-dialogue.srt").cuesAt(2_000_000)).lowest
        val webVtt = glyphRows(fixture("multi-line-dialogue.vtt").cuesAt(2_000_000)).lowest
        assertTrue(subRip < 1080 * 0.95, "SubRip keeps Media3's bottom padding: $subRip")
        assertEquals(subRip, webVtt)
    }

    @Test fun overlappingSubRipCuesStackInsteadOfDrawingOverEachOther() {
        assertEquals(2, renderedLines(fixture("multi-line-dialogue.srt").cuesAt(7_000_000)))
    }

    @Test fun laterWebVttCueStacksAboveATwoLineCue() {
        assertEquals(3, renderedLines(fixture("multi-line-dialogue.vtt").cuesAt(7_000_000)))
    }

    @Test fun overlappingCuesStackWithAProfileBackgroundAndLargerText() {
        val boxed = ProfilePreferences(subtitle_background_color = "#99000000")
        assertEquals(2, renderedLines(fixture("multi-line-dialogue.srt").cuesAt(7_000_000), preferences = boxed, scale = 1.5f))
    }

    @Test fun longLineWrapsOnAPhone() {
        assertEquals(2, renderedLines(fixture("multi-line-dialogue.srt").cuesAt(10_000_000), width = 1600, height = 720, scale = 1.5f))
    }

    /** PR evidence only: `VIPTV_SUBTITLE_CAPTURE_DIR=<dir>` writes Media3's raw drawing next to the stacked one. */
    @Test fun captureOverlappingCuesBeforeAndAfter() {
        val dir = System.getenv("VIPTV_SUBTITLE_CAPTURE_DIR") ?: return
        File(dir).mkdirs()
        val srt = fixture("multi-line-dialogue.srt").cuesAt(7_000_000)
        val vtt = fixture("multi-line-dialogue.vtt").cuesAt(7_000_000)
        renderedLines(srt, capture = "android-srt-overlap-before", stack = false)
        renderedLines(srt, capture = "android-srt-overlap-after")
        renderedLines(vtt, capture = "android-vtt-overlap-before", stack = false)
        renderedLines(vtt, capture = "android-vtt-overlap-after")
        renderedLines(fixture("multi-line-dialogue.srt").cuesAt(10_000_000), width = 1600, height = 720, scale = 1.5f, capture = "android-long-line-wraps")
    }

    @Test fun laterCueSitsAboveTheEarlierOneLikeMpvAndBrowsers() {
        val stacked = stackedCues(fixture("multi-line-dialogue.srt").cuesAt(7_000_000))
        assertEquals(listOf("- Home.\n- Where are you going?"), stacked.map { it.text.toString() })
    }

    @Test fun positionedCuesKeepTheirOwnPlace() {
        val top = Cue.Builder().setText("Sign on the wall").setLine(.1f, Cue.LINE_TYPE_FRACTION).build()
        val bottom = Cue.Builder().setText("Dialogue").build()
        assertEquals(listOf(top, bottom), stackedCues(listOf(top, bottom)))
    }
}
