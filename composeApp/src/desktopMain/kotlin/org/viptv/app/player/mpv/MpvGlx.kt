package org.viptv.app.player.mpv

import com.sun.jna.*
import com.sun.jna.Function
import com.sun.jna.ptr.IntByReference

internal interface MpvX11 : Library {
    fun XOpenDisplay(name: String?): Pointer?
    fun XCloseDisplay(display: Pointer): Int
    fun XDefaultScreen(display: Pointer): Int
    fun XFree(data: Pointer): Int
    companion object { val api: MpvX11 by lazy { Native.load("libX11.so.6", MpvX11::class.java) } }
}

internal interface MpvGlx : Library {
    fun glXGetCurrentContext(): Pointer?
    fun glXGetCurrentDisplay(): Pointer?
    fun glXChooseFBConfig(display: Pointer, screen: Int, attributes: IntArray, count: IntByReference): Pointer?
    fun glXCreateNewContext(display: Pointer, config: Pointer, renderType: Int, share: Pointer?, direct: Int): Pointer?
    fun glXDestroyContext(display: Pointer, context: Pointer)
    fun glXCreatePbuffer(display: Pointer, config: Pointer, attributes: IntArray): NativeLong
    fun glXDestroyPbuffer(display: Pointer, pbuffer: NativeLong)
    fun glXMakeContextCurrent(display: Pointer, draw: NativeLong, read: NativeLong, context: Pointer?): Int
    fun glXGetProcAddressARB(name: String): Pointer?
    fun glGenTextures(count: Int, textures: IntArray)
    fun glDeleteTextures(count: Int, textures: IntArray)
    fun glBindTexture(target: Int, texture: Int)
    fun glTexParameteri(target: Int, name: Int, value: Int)
    fun glTexImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, border: Int, format: Int, type: Int, pixels: Pointer?)
    fun glFinish()
    companion object { val api: MpvGlx by lazy { Native.load("libGL.so.1", MpvGlx::class.java) } }
}

internal object GlConst {
    const val TEXTURE_2D = 0x0DE1
    const val RGBA8 = 0x8058
    const val RGBA = 0x1908
    const val UNSIGNED_BYTE = 0x1401
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val LINEAR = 0x2601
    const val CLAMP_TO_EDGE = 0x812F
    const val FRAMEBUFFER = 0x8D40
    const val COLOR_ATTACHMENT0 = 0x8CE0
    const val FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GLX_RGBA_TYPE = 0x8014
    const val GLX_DRAWABLE_TYPE = 0x8010
    const val GLX_RENDER_TYPE = 0x8011
    const val GLX_PBUFFER_BIT = 4
    const val GLX_RGBA_BIT = 1
    const val GLX_RED_SIZE = 8
    const val GLX_GREEN_SIZE = 9
    const val GLX_BLUE_SIZE = 10
    const val GLX_PBUFFER_WIDTH = 0x8041
    const val GLX_PBUFFER_HEIGHT = 0x8040
}

// A GLX context sharing objects with [share], current on a 1x1 pbuffer. Its X resources are created and
// destroyed on the thread that constructs it; the render thread only makes it current and draws with it.
internal class MpvGlxContext(private val display: Pointer, share: Pointer?, private val screen: Int) : AutoCloseable {
    private val x11 = MpvX11.api
    val glx = MpvGlx.api
    private var pbuffer = NativeLong(0)
    private var context: Pointer? = null
    val handle: Pointer get() = requireNotNull(context)

    init {
        try {
            val count = IntByReference()
            val configs = requireNotNull(glx.glXChooseFBConfig(display, screen, intArrayOf(
                GlConst.GLX_DRAWABLE_TYPE, GlConst.GLX_PBUFFER_BIT, GlConst.GLX_RENDER_TYPE, GlConst.GLX_RGBA_BIT,
                GlConst.GLX_RED_SIZE, 8, GlConst.GLX_GREEN_SIZE, 8, GlConst.GLX_BLUE_SIZE, 8, 0), count)) { "No GLX pbuffer config" }
            try {
                check(count.value > 0) { "No GLX pbuffer config" }
                val config = configs.getPointer(0)
                context = requireNotNull(glx.glXCreateNewContext(display, config, GlConst.GLX_RGBA_TYPE, share, 1)) { "GLX context refused" }
                pbuffer = glx.glXCreatePbuffer(display, config, intArrayOf(GlConst.GLX_PBUFFER_WIDTH, 1, GlConst.GLX_PBUFFER_HEIGHT, 1, 0))
                check(pbuffer.toLong() != 0L) { "GLX pbuffer refused" }
            } finally { x11.XFree(configs) }
        } catch (failure: Throwable) {
            close(); throw failure
        }
    }

    fun makeCurrent() = check(glx.glXMakeContextCurrent(display, pbuffer, pbuffer, context) != 0) { "GLX context not current" }
    fun unbind() = glx.glXMakeContextCurrent(display, NativeLong(0), NativeLong(0), null)

    private val functions = HashMap<String, Function>()
    fun function(name: String): Function = functions.getOrPut(name) {
        Function.getFunction(requireNotNull(glx.glXGetProcAddressARB(name)) { "$name unavailable" })
    }

    // Strong ownership is essential: native mpv retains this function pointer.
    val procAddress = object : ProcAddress {
        override fun invoke(data: Pointer?, name: String): Pointer? = glx.glXGetProcAddressARB(name)
    }
    interface ProcAddress : Callback { fun invoke(data: Pointer?, name: String): Pointer? }

    override fun close() {
        if (pbuffer.toLong() != 0L) glx.glXDestroyPbuffer(display, pbuffer)
        context?.let { glx.glXDestroyContext(display, it) }
        pbuffer = NativeLong(0); context = null
    }
}