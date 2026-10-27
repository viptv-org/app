package org.viptv.app.features.settings

import coil3.PlatformContext
import kotlinx.browser.window

internal actual fun systemReducedMotion(context: PlatformContext): Boolean =
    runCatching { window.matchMedia("(prefers-reduced-motion: reduce)").matches }.getOrDefault(false)
