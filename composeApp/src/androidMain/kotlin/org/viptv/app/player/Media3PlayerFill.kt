package org.viptv.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@UnstableApi
internal fun PlayerView.applyFill(fill: Boolean) {
    resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
}