package org.viptv.app.player.mpv

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compose's desktop delegate records a SkPicture on the AWT thread, without a
 * current GL context. Use mpv's portable raster output there; native decoding
 * can still use copy-back acceleration. Each immutable frame has explicit
 * ownership, independent of the Java heap's native-memory accounting.
 */
internal class MpvRenderer(private val session: MpvSession, private val host: MpvRenderHost, private val invalidate: () -> Unit) {
    private val api = session.api
    private var context: Pointer? = null
    private var width = 0
    private var height = 0
    @Volatile var desiredWidth = 0
    @Volatile var desiredHeight = 0
    var image: Image? = null; private set
    var generation: Long = 0; private set
    var framePositionMs: Long = 0; private set
    val path = "mpv-software"
    private val stopped = AtomicBoolean(false)
    private var failed = false
    // Strong ownership is essential: native mpv retains this function pointer.
    private val updateCallback = object : MpvNative.UpdateCallback {
        override fun invoke(data: Pointer?) { if (!stopped.get()) { invalidate(); host.redraw() } }
    }

    fun start() = host.add(this)
    fun authorizePresentation() { session.presentationAllowed = true; invalidate(); host.redraw() }
    fun close() { if (stopped.compareAndSet(false, true)) host.remove(this) }
    fun prepare() {
        if (stopped.get() || failed) return
        val w = desiredWidth.coerceIn(0, 8192)
        val h = desiredHeight.coerceIn(0, 8192)
        if (w < 1 || h < 1) return
        try {
            if (context == null) {
                MpvParams().use { p ->
                    p.add(1, p.text("sw"))
                    val result = PointerByReference()
                    mpvCheck(api.mpv_render_context_create(result, session.handle, p.pointer()))
                    context = requireNotNull(result.value)
                }
                api.mpv_render_context_set_update_callback(requireNotNull(context), updateCallback, null)
                session.rendererReady.complete(Unit)
            }
            val ctx = requireNotNull(context)
            val changed = api.mpv_render_context_update(ctx) and 1L != 0L
            val first = generation == 0L && session.presentationAllowed && session.state.value.width > 0
            if (!changed && !first && width == w && height == h) return
            val stride = (w * 4 + 63) and -64
            Data.makeUninitialized(Math.multiplyExact(stride, h)).use { data ->
                MpvParams().use { p ->
                    p.add(17, p.memory(8).also { it.setInt(0, w); it.setInt(4, h) })
                    p.add(18, p.text("rgb0")); p.add(19, p.long(stride.toLong()))
                    p.add(20, Pointer(data.writableData())); p.add(12, p.int(0))
                    mpvCheck(api.mpv_render_context_render(ctx, p.pointer()))
                }
                val replacement = Image.makeRaster(ImageInfo(w, h, ColorType.RGB_888X, ColorAlphaType.OPAQUE), data, stride)
                image?.close()
                image = replacement
            }
            width = w; height = h
            if ((changed || first) && session.presentationAllowed && session.state.value.width > 0) {
                framePositionMs = session.state.value.videoMs
                generation++
            }
        } catch (error: Throwable) {
            failed = true
            session.rendererReady.completeExceptionally(error)
            session.rendererFailed()
        }
    }
    fun drawn() { context?.let { api.mpv_render_context_report_swap(it) } }
    fun destroy() {
        try {
            context?.let { api.mpv_render_context_set_update_callback(it, null, null); api.mpv_render_context_free(it) }
            context = null
            image?.close(); image = null
        } finally { session.closeAfterRenderer() }
    }
}
