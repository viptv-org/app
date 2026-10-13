package org.viptv.app.player

import android.os.Build
import io.github.yuroyami.kiteplayer.compose.KiteRenderPath

// Kite's window-bound Compose renderer keeps frames on the GPU from API 31 onward.
// Drawing video inside Compose lets the TV track drawer capture it for backdrop blur.
// Earlier Android versions use a CPU conversion path, so retain their native renderer.
internal actual fun nativePlayerRenderPath(): KiteRenderPath =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) KiteRenderPath.ComposeCanvas
    else KiteRenderPath.Auto
