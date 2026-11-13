package org.viptv.app.features.settings

import coil3.PlatformContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
internal actual fun systemReducedMotion(context: PlatformContext): Boolean = remember(context) {
    runCatching { window.matchMedia("(prefers-reduced-motion: reduce)").matches }.getOrDefault(false)
}
