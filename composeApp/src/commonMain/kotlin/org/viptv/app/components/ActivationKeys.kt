package org.viptv.app.components

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class InputSurface { Television, Touch, Browser, Desktop }

internal val InputSurface.bindsActivationKeys: Boolean get() = this != InputSurface.Desktop

internal fun inputSurface(television: Boolean, browser: Boolean, desktop: Boolean): InputSurface = when {
    television -> InputSurface.Television
    desktop -> InputSurface.Desktop
    browser -> InputSurface.Browser
    else -> InputSurface.Touch
}

internal val LocalActivationKeys = staticCompositionLocalOf { true }
