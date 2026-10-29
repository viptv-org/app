package org.viptv.app.features.settings

import android.provider.Settings
import coil3.PlatformContext

// Developer options' "Remove animations" (and accessibility's) set the animator scale to 0.
internal actual fun systemReducedMotion(context: PlatformContext): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
