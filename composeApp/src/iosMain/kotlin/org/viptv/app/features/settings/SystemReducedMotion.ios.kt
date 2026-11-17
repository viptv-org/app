package org.viptv.app.features.settings

import coil3.PlatformContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
internal actual fun systemReducedMotion(context: PlatformContext): Boolean = remember(context) {
    UIAccessibilityIsReduceMotionEnabled()
}
