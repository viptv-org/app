package org.viptv.app.features.sources

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

internal actual fun browserSourceDrawerProperties() =
    DialogProperties(dismissOnBackPress = false, usePlatformDefaultWidth = false, scrimColor = Color.Transparent)
