package org.viptv.app.connection

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

object AndroidDisplayCapability {
    @Volatile internal var context: Context? = null
    fun attach(context: Context) { this.context = context.applicationContext }
}

// The largest mode the display supports, not the current one: a 4K TV can run its UI at 1080p. Landscape: the short side is the height.
internal actual fun platformDisplayShortSide(): Int? {
    val context = AndroidDisplayCapability.context ?: return null
    return try {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        display.supportedModes.maxOfOrNull { minOf(it.physicalWidth, it.physicalHeight) }
    } catch (_: RuntimeException) { null }
}
