package org.viptv.app.player.mpv

import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Three textures of one size; whichever side claims it first owns the GL textures:
// the EDT by wrapping them in Skia images, the render thread by deleting them unseen.
internal class MpvTextureSet(val width: Int, val height: Int, val textures: IntArray, val framebuffers: IntArray) {
    private val claimed = AtomicBoolean(false)
    fun claim() = claimed.compareAndSet(false, true)
}

internal class MpvGlFrame(val set: MpvTextureSet, val slot: Int) { val texture get() = set.textures[slot] }

// Renders mpv into shared GL textures on its own thread, so mpv's pacing never waits for a Compose frame.
internal class MpvGlPresenter(
    private val api: MpvNative,
    private val handle: Pointer,
    private val gl: MpvGlxContext,
    private val onFrame: () -> Unit,
) {
    private val ring = MpvFrameRing()
    private val frames = arrayOfNulls<MpvGlFrame>(3)
    private val wake = Semaphore(0)
    private val ready = CompletableFuture<Unit>()
    @Volatile private var running = true
    @Volatile private var desiredWidth = 0
    @Volatile private var desiredHeight = 0
    @Volatile var failure: Throwable? = null; private set
    // Strong ownership is essential: native mpv retains this function pointer.
    private val updateCallback = object : MpvNative.UpdateCallback {
        override fun invoke(data: Pointer?) { wake.release() }
    }
    private val thread = Thread(::run, "mpv-render").apply { isDaemon = true }

    fun start(timeoutMs: Long = 5_000) {
        thread.start()
        try { ready.get(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (failure: Exception) { close(); throw failure.cause ?: failure }
    }

    fun resize(width: Int, height: Int) {
        if (width == desiredWidth && height == desiredHeight) return
        desiredWidth = width; desiredHeight = height; wake.release()
    }

    fun acquire(): MpvGlFrame? = if (ring.acquire()) frames[ring.front] else null

    // The GL context's X resources belong to the thread that created it, so they are released here, once
    // the render thread has stopped using it.
    fun close() {
        running = false; wake.release()
        if (thread.isAlive && Thread.currentThread() != thread) thread.join(5_000)
        if (!thread.isAlive) gl.close()
    }

    private fun run() {
        var context: Pointer? = null
        var set: MpvTextureSet? = null
        try {
            gl.makeCurrent()
            MpvParams().use { p ->
                // mpv_opengl_init_params: two pointers, plus extra_exts on libmpv 2.3 and later, which stays NULL.
                val init = p.memory(24).also { it.setPointer(0, CallbackReference.getFunctionPointer(gl.procAddress)) }
                p.add(1, p.text("opengl")); p.add(2, init)
                val result = PointerByReference()
                mpvCheck(api.mpv_render_context_create(result, handle, p.pointer()))
                context = requireNotNull(result.value)
            }
            api.mpv_render_context_set_update_callback(requireNotNull(context), updateCallback, null)
            ready.complete(Unit)
            while (running) {
                wake.tryAcquire(100, TimeUnit.MILLISECONDS); wake.drainPermits()
                if (!running) break
                val w = desiredWidth.coerceIn(0, 8192)
                val h = desiredHeight.coerceIn(0, 8192)
                if (w < 1 || h < 1) continue
                val resized = set == null || set.width != w || set.height != h
                if (resized) { set?.let { retire(it) }; set = allocate(w, h) }
                val current = requireNotNull(set)
                val changed = api.mpv_render_context_update(requireNotNull(context)) and 1L != 0L
                if (!changed && !resized) continue
                MpvParams().use { p ->
                    val fbo = p.memory(16).also { it.setInt(0, current.framebuffers[ring.back]); it.setInt(4, w); it.setInt(8, h); it.setInt(12, 0) }
                    // This thread reports its own swaps, so mpv must not block the render waiting for one.
                    p.add(3, fbo); p.add(4, p.int(0)); p.add(12, p.int(0))
                    mpvCheck(api.mpv_render_context_render(requireNotNull(context), p.pointer()))
                }
                gl.glx.glFinish()
                frames[ring.back] = MpvGlFrame(current, ring.back)
                ring.publish()
                api.mpv_render_context_report_swap(requireNotNull(context))
                onFrame()
            }
        } catch (error: Throwable) {
            failure = error
            if (!ready.completeExceptionally(error)) onFrame()
        } finally {
            context?.let { api.mpv_render_context_set_update_callback(it, null, null); api.mpv_render_context_free(it) }
            set?.let(::retire)
            gl.unbind()
        }
    }

    private fun allocate(width: Int, height: Int): MpvTextureSet {
        val glx = gl.glx
        val textures = IntArray(3).also { glx.glGenTextures(3, it) }
        val framebuffers = IntArray(3).also { gl.function("glGenFramebuffers").invokeVoid(arrayOf(3, it)) }
        for (i in 0 until 3) {
            glx.glBindTexture(GlConst.TEXTURE_2D, textures[i])
            glx.glTexParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MIN_FILTER, GlConst.LINEAR)
            glx.glTexParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_MAG_FILTER, GlConst.LINEAR)
            glx.glTexParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_S, GlConst.CLAMP_TO_EDGE)
            glx.glTexParameteri(GlConst.TEXTURE_2D, GlConst.TEXTURE_WRAP_T, GlConst.CLAMP_TO_EDGE)
            glx.glTexImage2D(GlConst.TEXTURE_2D, 0, GlConst.RGBA8, width, height, 0, GlConst.RGBA, GlConst.UNSIGNED_BYTE, null)
            gl.function("glBindFramebuffer").invokeVoid(arrayOf(GlConst.FRAMEBUFFER, framebuffers[i]))
            gl.function("glFramebufferTexture2D").invokeVoid(arrayOf(GlConst.FRAMEBUFFER, GlConst.COLOR_ATTACHMENT0, GlConst.TEXTURE_2D, textures[i], 0))
            check(gl.function("glCheckFramebufferStatus").invokeInt(arrayOf(GlConst.FRAMEBUFFER)) == GlConst.FRAMEBUFFER_COMPLETE) { "Incomplete mpv framebuffer" }
        }
        gl.function("glBindFramebuffer").invokeVoid(arrayOf(GlConst.FRAMEBUFFER, 0))
        glx.glBindTexture(GlConst.TEXTURE_2D, 0)
        return MpvTextureSet(width, height, textures, framebuffers)
    }

    private fun retire(set: MpvTextureSet) {
        gl.function("glDeleteFramebuffers").invokeVoid(arrayOf(3, set.framebuffers))
        if (set.claim()) gl.glx.glDeleteTextures(3, set.textures)
        gl.glx.glFinish()
    }
}
