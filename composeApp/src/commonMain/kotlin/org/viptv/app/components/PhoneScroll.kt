package org.viptv.app.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class PhoneScroll {
    var scrolled by mutableStateOf(false)
        private set

    internal fun onScroll(canScrollBackward: Boolean) { scrolled = canScrollBackward }
}

internal val LocalPhoneScroll = compositionLocalOf<PhoneScroll?> { null }