package org.viptv.app.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class PhoneScroll {
    var scrolled by mutableStateOf(false)
        private set
    var barHidden by mutableStateOf(false)
        private set

    private var upDistance = 0f
    private var reveal = 0f

    internal fun revealAtPx(px: Float) { reveal = px }

    internal fun onScrolled(canScrollBackward: Boolean) {
        scrolled = canScrollBackward
        if (!canScrollBackward) { upDistance = 0f; barHidden = false }
    }

    // The actual scroll gesture direction: negative = down the page, positive = up.
    internal fun onScrollDelta(delta: Float) {
        if (delta < 0f) { barHidden = true; upDistance = 0f }
        else { upDistance += delta; if (upDistance >= reveal) barHidden = false }
    }
}

internal val LocalPhoneScroll = compositionLocalOf<PhoneScroll?> { null }