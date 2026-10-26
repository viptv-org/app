package org.viptv.experiment

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal fun ComponentActivity.configurePlayerWindow(active: Boolean, television: Boolean) {
    val bars = WindowCompat.getInsetsController(window, window.decorView)
    if (active) {
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    } else bars.show(WindowInsetsCompat.Type.systemBars())
    if (television) return
    requestedOrientation = if (active) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    // The full-screen player draws under the display cutout on both landscape sides; its controls keep
    // the safe-drawing insets. Other screens keep the default window, so their insets are unchanged.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // API 35+ is edge to edge already (targetSdk 35+). Below it the decor must stop fitting the
        // system windows, or the content is laid out inside them and never reaches the cutout.
        WindowCompat.setDecorFitsSystemWindows(window, !active)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = playerCutoutMode(active, Build.VERSION.SDK_INT)
        if (window.attributes.layoutInDisplayCutoutMode != mode) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = mode }
        }
    }
}

/** Window cutout mode for a handheld: into the cutout only while the player is full screen. */
internal fun playerCutoutMode(active: Boolean, sdk: Int): Int = when {
    !active -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    sdk >= Build.VERSION_CODES.R -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
