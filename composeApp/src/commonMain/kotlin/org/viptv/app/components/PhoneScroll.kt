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

    private var peak = 0f
    private var reveal = 0f

    internal fun revealAtPx(px: Float) { reveal = px }

    internal fun onPosition(canScrollBackward: Boolean, top: Float) {
        scrolled = canScrollBackward
        val clamped = top.coerceAtLeast(0f)
        when {
            clamped <= 0f -> { barHidden = false; peak = 0f }
            clamped >= peak -> { peak = clamped; barHidden = true }
            peak - clamped >= reveal -> { barHidden = false; peak = clamped }
        }
    }
}

internal val LocalPhoneScroll = compositionLocalOf<PhoneScroll?> { null }

internal const val PhoneScrollStep = 1_000_000