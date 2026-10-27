package org.viptv.app.features.settings

import coil3.PlatformContext
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

internal actual fun systemReducedMotion(context: PlatformContext): Boolean = UIAccessibilityIsReduceMotionEnabled()
