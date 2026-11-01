package org.viptv.app.components

import kotlin.math.sqrt

private val os = System.getProperty("os.name").orEmpty()

// Compose steps a vertical wheel notch by √extent on Linux and extent/20 on Windows, where extent is the row's height; macOS steps a fixed 10dp.
internal actual fun sidewaysWheelScale(width: Int, height: Int): Float = when {
    width <= 0 || height <= 0 -> 1f
    os.startsWith("Linux") -> sqrt(width.toFloat() / height)
    os.startsWith("Windows") -> width.toFloat() / height
    else -> 1f
}
