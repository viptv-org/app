package org.viptv.app

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.*
import org.viptv.app.player.ErrorSlateDetector
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.SlateSampleHeight
import org.viptv.app.player.SlateSampleWidth
import org.viptv.app.player.mpv.MpvLocale
import org.viptv.app.player.mpv.MpvNative
import org.viptv.app.player.mpv.MpvParams
import org.viptv.app.player.mpv.MpvSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The detector against real decoded frames: lavfi renders the clips, so no fixture is checked in and no network is used.
class ErrorSlateClipTest {
    private val api = MpvNative.api
    private val width = 640
    private val height = 360
    private val stride = (width * 4 + 63) and -64

    @Test fun aSlateVideoIsReportedAndAMovingOrSlowSceneNeverIs() = runBlocking {
        MpvLocale.requireNumericCLocale()
        assertEquals(true, detect("av://lavfi:color=c=0x101418:s=640x360:r=25:d=40"), "a still image for its whole length")
        assertEquals(false, detect("av://lavfi:testsrc2=s=640x360:r=25:d=40"), "a moving test pattern")
        assertEquals(false, detect("av://lavfi:smptebars=s=800x450:r=25:d=40,crop=640:360:0.5*t:0"), "a slow pan over a still shot")
    }

    private suspend fun detect(url: String): Boolean {
        val session = MpvSession(PlaybackInput("slate-clip", url))
        val context = MpvParams().use { p ->
            p.add(1, p.text("sw"))
            PointerByReference().also { check(api.mpv_render_context_create(it, session.handle, p.pointer()) >= 0) }.value
        }
        val detector = ErrorSlateDetector()
        var sampled = false
        var reported = false
        try {
            session.property("hwdec", "no")
            session.property("aid", "no")
            session.command("loadfile", url, "replace")
            session.property("pause", "no")
            // 20 s of the clip in 2.5 s of wall clock: the sample points are the clip's own clock.
            session.property("speed", "8")
            val deadline = System.nanoTime() + 20_000_000_000L
            while (!detector.done && System.nanoTime() < deadline) {
                val position = ((number(session, "time-pos") ?: 0.0) * 1000).toLong()
                if (!detector.due(position)) { Thread.sleep(2); continue }
                val gray = render(context)
                if (gray == null) { Thread.sleep(2); continue }
                sampled = true
                if (detector.take(position, gray, lowBitrate = false)) reported = true
            }
            assertTrue(detector.done, "all three samples of $url")
        } finally {
            api.mpv_render_context_free(context)
            session.closeAfterRenderer()
            withTimeout(5_000) { session.closed.await() }
        }
        assertTrue(sampled, "the clip produced samples: $url")
        return reported
    }

    private fun render(context: Pointer): ByteArray? {
        if (api.mpv_render_context_update(context) and 1L == 0L) return null
        val data = Data.makeUninitialized(Math.multiplyExact(stride, height))
        MpvParams().use { p ->
            p.add(17, p.memory(8).also { it.setInt(0, width); it.setInt(4, height) })
            p.add(18, p.text("rgb0")); p.add(19, p.long(stride.toLong()))
            p.add(20, Pointer(data.writableData())); p.add(12, p.int(0))
            check(api.mpv_render_context_render(context, p.pointer()) >= 0)
        }
        Image.makeRaster(ImageInfo(width, height, ColorType.RGB_888X, ColorAlphaType.OPAQUE), data, stride).use { image ->
            Bitmap().use { bitmap ->
                bitmap.allocPixels(ImageInfo(SlateSampleWidth, SlateSampleHeight, ColorType.GRAY_8, ColorAlphaType.OPAQUE))
                val pixmap = bitmap.peekPixels() ?: return null
                image.scalePixels(pixmap, SamplingMode.LINEAR, true)
                return bitmap.readPixels()
            }
        }
    }

    private fun number(session: MpvSession, name: String): Double? =
        session.api.mpv_get_property_string(session.handle, name)?.let { pointer ->
            try { pointer.getString(0, "UTF-8").toDoubleOrNull() } finally { session.api.mpv_free(pointer) }
        }
}