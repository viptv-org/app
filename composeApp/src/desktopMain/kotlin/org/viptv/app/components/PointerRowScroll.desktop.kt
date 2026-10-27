package org.viptv.app.components

import kotlin.math.sqrt

private val linux = System.getProperty("os.name").orEmpty().startsWith("Linux")

/** Compose's Linux (GNOME) wheel config scales a step by √extent; Windows and macOS use fixed steps. */
internal actual fun sidewaysWheelScale(width: Int, height: Int): Float =
    if (linux && width > 0 && height > 0) sqrt(width.toFloat() / height) else 1f
