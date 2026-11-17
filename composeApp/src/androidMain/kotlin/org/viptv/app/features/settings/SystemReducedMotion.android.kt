package org.viptv.app.features.settings

import android.provider.Settings
import coil3.PlatformContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Developer options' "Remove animations" (and accessibility's) set the animator scale to 0.
@Composable
internal actual fun systemReducedMotion(context: PlatformContext): Boolean = remember(context) {
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}
