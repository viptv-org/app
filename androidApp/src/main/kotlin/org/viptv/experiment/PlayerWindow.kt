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
    // Only the full-screen player draws under the cutout; its controls keep the safe-drawing insets.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // Below API 35 the decor must stop fitting system windows or the content never reaches the cutout.
        WindowCompat.setDecorFitsSystemWindows(window, !active)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = playerCutoutMode(active)
        if (window.attributes.layoutInDisplayCutoutMode != mode) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = mode }
        }
    }
}

private fun playerCutoutMode(active: Boolean): Int = when {
    !active -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}
