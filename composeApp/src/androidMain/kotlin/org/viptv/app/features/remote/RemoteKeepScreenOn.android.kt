package org.viptv.app.features.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun RemoteKeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        val original = view.keepScreenOn
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = original }
    }
}