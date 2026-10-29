package org.viptv.app.connection

import kotlinx.browser.window

// CSS pixels times the device pixel ratio approximates the screen's physical short side.
internal actual fun platformDisplayShortSide(): Int? = try {
    val screen = window.screen
    (minOf(screen.width, screen.height) * window.devicePixelRatio).toInt().takeIf { it > 0 }
} catch (_: Throwable) { null }
