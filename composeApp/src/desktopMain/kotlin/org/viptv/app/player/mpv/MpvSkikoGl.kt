package org.viptv.app.player.mpv

import com.sun.jna.Platform
import com.sun.jna.Pointer
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer

// Skiko's own GL objects: its DirectContext, and the display connection and GLX context it has current.
internal class MpvSkikoGl(val directContext: DirectContext, val display: Pointer, val shareContext: Pointer, val screen: Int)

// Skiko exposes the DirectContext only as a private field; it is pinned by the Compose version, and any
// mismatch reads as Unavailable so playback stays on the software path.
internal fun probeSkikoGl(layer: SkiaLayer): MpvGlAvailability {
    if (!Platform.isLinux() || layer.renderApi != GraphicsApi.OPENGL) return MpvGlAvailability.Unavailable
    val glx = MpvGlx.api
    val display = glx.glXGetCurrentDisplay() ?: return MpvGlAvailability.Unavailable
    val share = glx.glXGetCurrentContext() ?: return MpvGlAvailability.Pending
    return try {
        val driver = layer.javaClass.getMethod("getFrameDriver\$skiko").invoke(layer) ?: return MpvGlAvailability.Pending
        val renderer = privateField(privateField(driver, "producer"), "renderer")
        if (renderer?.javaClass?.name != "org.jetbrains.skiko.renderer.LinuxOpenGLRenderer") return MpvGlAvailability.Unavailable
        val context = privateField(renderer, "glContext") as DirectContext?
        if (context == null) MpvGlAvailability.Pending
        else MpvGlAvailability.Ready(MpvSkikoGl(context, display, share, MpvX11.api.XDefaultScreen(display)))
    } catch (_: ReflectiveOperationException) {
        MpvGlAvailability.Unavailable
    } catch (_: RuntimeException) {
        MpvGlAvailability.Unavailable
    }
}

private fun privateField(owner: Any?, name: String): Any? {
    var type: Class<*>? = requireNotNull(owner).javaClass
    while (type != null) {
        type.declaredFields.firstOrNull { it.name == name }?.let { it.isAccessible = true; return it.get(owner) }
        type = type.superclass
    }
    throw NoSuchFieldException(name)
}