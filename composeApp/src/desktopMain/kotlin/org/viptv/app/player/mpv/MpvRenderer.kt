package org.viptv.app.player.mpv

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.*
import org.viptv.app.player.SlateSampleHeight
import org.viptv.app.player.SlateSampleWidth
import java.util.concurrent.atomic.AtomicBoolean

// Presenting through mpv's software render context is the default; the GL path composites Skiko's own
// texture images instead, which needs the DirectContext Skiko never exposes publicly.
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
    @Volatile private var renderPath: MpvRenderPath? = null
    val path: String get() = renderPath?.label ?: "pending"
    @Volatile var glStatus = "pending"; private set
    private var pendingFrames = 0
    private var gl: MpvGlPresenter? = null
    private var glInterop: MpvSkikoGl? = null
    private val glImages = HashMap<MpvTextureSet, List<Image>>()
    private var glFrameWaiting = false
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
            val path = renderPath ?: choosePath() ?: return
            when (path) {
                MpvRenderPath.Software -> prepareSoftware(w, h)
                MpvRenderPath.Gl -> prepareGl(w, h)
            }
        } catch (error: Throwable) {
            failed = true
            session.rendererReady.completeExceptionally(error)
            session.rendererFailed()
        }
    }

    // Skiko creates its GL context during its first frame, so the choice waits a bounded number of frames.
    private fun choosePath(): MpvRenderPath? {
        val requested = mpvGlRequested()
        val availability = if (requested) host.skikoGl() else MpvGlAvailability.Unavailable
        val path = mpvRenderPath(requested, availability.state, pendingFrames++)
        if (path == null) { host.redraw(); return null }
        if (path == MpvRenderPath.Gl) {
            val interop = (availability as MpvGlAvailability.Ready).interop
            session.applyColourOptions(MpvRenderPath.Gl)
            val context = runCatching { MpvGlxContext(interop.display, interop.shareContext, interop.screen) }.getOrNull()
            if (context != null) {
                val presenter = MpvGlPresenter(api, session.handle, context) { if (!stopped.get()) { invalidate(); host.redraw() } }
                if (runCatching { presenter.start() }.isSuccess) {
                    gl = presenter; glInterop = interop; glStatus = "on"
                    return select(MpvRenderPath.Gl)
                }
            }
            glStatus = "failed"
        } else glStatus = if (requested) "unavailable" else "off"
        return select(MpvRenderPath.Software)
    }

    private fun select(path: MpvRenderPath): MpvRenderPath {
        renderPath = path
        session.renderPath = path
        if (path == MpvRenderPath.Gl) session.rendererReady.complete(Unit)
        return path
    }

    private fun prepareGl(w: Int, h: Int) {
        val presenter = requireNotNull(gl)
        if (presenter.failure != null) return fallBackToSoftware()
        presenter.resize(w, h)
        presenter.acquire()?.let { frame ->
            val images = glImages[frame.set] ?: adopt(frame.set)?.also { glImages[frame.set] = it }
            if (images != null) {
                glImages.keys.filter { it !== frame.set }.forEach { stale -> glImages.remove(stale)?.let(::release) }
                image = images[frame.slot]
                glFrameWaiting = true
            }
        }
        if (glFrameWaiting && session.presentationAllowed && session.state.value.width > 0) {
            glFrameWaiting = false
            framePositionMs = session.state.value.videoMs
            generation++
        }
    }

    // Skiko records this draw and plays it back on its own GL context in the same tick, so the adopted
    // textures belong to the context current here.
    private fun adopt(set: MpvTextureSet): List<Image>? {
        if (!set.claim()) return null
        val interop = requireNotNull(glInterop)
        return set.textures.map { texture ->
            Image.adoptTextureFrom(interop.directContext,
                BackendTexture.makeGL(set.width, set.height, false, texture, GlConst.TEXTURE_2D, GlConst.RGBA8),
                SurfaceOrigin.TOP_LEFT, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        }
    }

    // The images own the textures they were adopted from, so closing them deletes the textures here.
    private fun release(images: List<Image>) { images.forEach(Image::close) }

    private fun fallBackToSoftware() {
        glStatus = "failed"
        gl?.close(); gl = null; glInterop = null
        glImages.forEach { (_, images) -> release(images) }
        glImages.clear()
        glFrameWaiting = false
        image = null
        select(MpvRenderPath.Software)
    }

    private fun prepareSoftware(w: Int, h: Int) {
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
    }

    // The presented picture at 32x18 gray: one scaled draw of the frame the UI already holds (~0.15 ms).
    fun graySample(): ByteArray? {
        if (sampleFailed) return null
        return runCatching {
            val source = image?.takeIf { !stopped.get() } ?: return null
            val bitmap = sampleBitmap ?: Bitmap().apply {
                allocPixels(ImageInfo(SlateSampleWidth, SlateSampleHeight, ColorType.GRAY_8, ColorAlphaType.OPAQUE))
            }.also { sampleBitmap = it }
            val pixmap = bitmap.peekPixels() ?: return null
            check(source.scalePixels(pixmap, SamplingMode.LINEAR, true))
            bitmap.readPixels()
        }.getOrElse { sampleFailed = true; null }
    }
    private var sampleBitmap: Bitmap? = null
    private var sampleFailed = false

    fun drawn() { if (renderPath != MpvRenderPath.Gl) context?.let { api.mpv_render_context_report_swap(it) } }
    fun destroy() {
        try {
            gl?.close(); gl = null
            context?.let { api.mpv_render_context_set_update_callback(it, null, null); api.mpv_render_context_free(it) }
            context = null
            glImages.forEach { (_, images) -> release(images) }
            glImages.clear()
            if (renderPath != MpvRenderPath.Gl) image?.close()
            image = null
            sampleBitmap?.close(); sampleBitmap = null
        } finally { session.closeAfterRenderer() }
    }
}
