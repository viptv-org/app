package org.viptv.app.connection

import java.awt.GraphicsEnvironment

// The largest attached screen's short side in physical pixels (display modes are not scaled by the OS).
internal actual fun platformDisplayShortSide(): Int? = try {
    if (GraphicsEnvironment.isHeadless()) null
    else GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .mapNotNull { device -> device.displayMode?.let { minOf(it.width, it.height) }?.takeIf { it > 0 } }.maxOrNull()
} catch (_: Exception) { null }
