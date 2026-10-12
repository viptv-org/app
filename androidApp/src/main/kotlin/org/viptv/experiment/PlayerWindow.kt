package org.viptv.experiment

import android.content.pm.ActivityInfo
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
    if (!television) requestedOrientation = if (active) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}
