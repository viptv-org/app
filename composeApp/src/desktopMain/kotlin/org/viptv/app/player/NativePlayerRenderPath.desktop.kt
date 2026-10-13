package org.viptv.app.player

import io.github.yuroyami.kiteplayer.compose.KiteRenderPath

// Keep video in the Compose scene so controls receive input and draw above it.
// Kite 0.3.0 converts desktop frames on the CPU; hardware/4K performance remains unqualified.
internal actual fun nativePlayerRenderPath(): KiteRenderPath = KiteRenderPath.ComposeCanvas
