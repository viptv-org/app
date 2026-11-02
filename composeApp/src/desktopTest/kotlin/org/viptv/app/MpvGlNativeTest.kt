package org.viptv.app

import com.sun.jna.Memory
import com.sun.jna.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.mpv.*
import kotlin.test.*

class MpvGlNativeTest {
    @Test fun mpvRendersIntoTexturesThatASharingContextReadsTopRowFirst() = runBlocking {
        assumeTrue("Opt-in on a display with a hardware GLX driver", Platform.isLinux() && System.getenv("VIPTV_MPV_GL_TEST") == "1")
        assumeTrue("GLX needs an X display", !System.getenv("DISPLAY").isNullOrBlank())
        val x11 = MpvX11.api
        val display = requireNotNull(x11.XOpenDisplay(null)) { "No X display" }
        val screen = x11.XDefaultScreen(display)
        val reader = runCatching { MpvGlxContext(display, null, screen) }.getOrNull()
        assumeTrue("No GLX pbuffer context on this display", reader != null)
        requireNotNull(reader)
        reader.makeCurrent()
        val session = MpvSession(PlaybackInput("owned", "about:blank"))
        val fbo = IntArray(1)
        var renderContext: MpvGlxContext? = null
        var presenter: MpvGlPresenter? = null
        try {
            val context = MpvGlxContext(display, reader.handle, screen).also { renderContext = it }
            val renderer = MpvGlPresenter(session.api, session.handle, context) {}.also { presenter = it }
            session.property("hwdec", "no")
            session.property("pause", "no")
            renderer.start()
            renderer.resize(64, 128)
            session.command("loadfile", "av://lavfi:color=c=red:s=64x64:d=5,pad=64:128:0:0:blue", "replace")
            reader.function("glGenFramebuffers").invokeVoid(arrayOf(1, fbo))
            reader.function("glBindFramebuffer").invokeVoid(arrayOf(GlConst.FRAMEBUFFER, fbo[0]))
            fun pixel(frame: MpvGlFrame, y: Int): List<Int> = Memory(4).use { out ->
                reader.function("glFramebufferTexture2D").invokeVoid(arrayOf(GlConst.FRAMEBUFFER, GlConst.COLOR_ATTACHMENT0, GlConst.TEXTURE_2D, frame.texture, 0))
                reader.function("glReadPixels").invokeVoid(arrayOf(32, y, 1, 1, GlConst.RGBA, GlConst.UNSIGNED_BYTE, out))
                (0 until 3).map { out.getByte(it.toLong()).toInt() and 0xFF }
            }
            var seen: MpvGlFrame? = null
            var top = emptyList<Int>()
            var bottom = emptyList<Int>()
            withTimeoutOrNull(15_000) {
                while (true) {
                    assertNull(renderer.failure)
                    renderer.acquire()?.let { frame ->
                        seen = frame
                        assertEquals(64 to 128, frame.set.width to frame.set.height)
                        top = pixel(frame, 0); bottom = pixel(frame, 127)
                    }
                    if (top.isNotEmpty() && (top[0] > 200 || top[2] > 200)) break
                    delay(20)
                }
            }
            val frame = assertNotNull(seen, "mpv published no frame")
            assertNotEquals(0, reader.function("glIsTexture").invokeInt(arrayOf(frame.texture)), "the sharing context cannot see the rendered texture")
            assertTrue(top[0] > 200 && top[2] < 60, "texture row 0 is the top of the picture (red): $top")
            assertTrue(bottom[2] > 200 && bottom[0] < 60, "the last texture row is the bottom (blue): $bottom")
        } finally {
            if (fbo[0] != 0) reader.function("glDeleteFramebuffers").invokeVoid(arrayOf(1, fbo))
            val stopping = presenter
            if (stopping != null) stopping.close() else renderContext?.close()
            session.closeAfterRenderer()
            withTimeout(5_000) { session.closed.await() }
            reader.close()
            x11.XCloseDisplay(display)
        }
    }
}