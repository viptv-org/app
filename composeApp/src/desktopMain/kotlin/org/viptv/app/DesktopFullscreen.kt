package org.viptv.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement

internal typealias FloatingBounds = Pair<DpSize, DpOffset>

internal class DesktopWindowPlacement(initial: WindowPlacement = WindowPlacement.Floating) {
    var placement by mutableStateOf(initial)
        private set
    var preFullscreen by mutableStateOf<WindowPlacement?>(null)
        private set
    var floatingBounds by mutableStateOf<FloatingBounds?>(null)
        private set

    val fullscreen get() = placement == WindowPlacement.Fullscreen
    val maximized get() = placement == WindowPlacement.Maximized

    fun takeFloatingBounds(): FloatingBounds? {
        val bounds = floatingBounds
        floatingBounds = null
        return bounds
    }

    fun maximize(bounds: FloatingBounds) {
        if (placement == WindowPlacement.Maximized) {
            placement = WindowPlacement.Floating
        } else {
            floatingBounds = bounds
            placement = WindowPlacement.Maximized
        }
    }

    fun toggleFullscreen(bounds: FloatingBounds) {
        if (placement == WindowPlacement.Fullscreen) {
            placement = preFullscreen ?: WindowPlacement.Floating
            preFullscreen = null
        } else {
            preFullscreen = placement
            if (placement == WindowPlacement.Floating) floatingBounds = bounds
            placement = WindowPlacement.Fullscreen
        }
    }
}