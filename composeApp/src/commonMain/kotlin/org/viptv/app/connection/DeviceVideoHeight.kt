package org.viptv.app.connection

internal expect fun platformDisplayShortSide(): Int?

internal fun snapVideoHeight(pixels: Int): Int = when {
    pixels >= 2160 -> 2160
    pixels >= 1440 -> 1440
    pixels >= 1080 -> 1080
    pixels >= 720 -> 720
    else -> 480
}

private var resolvedVideoHeight: Int? = null

fun deviceMaxVideoHeight(): Int? = resolvedVideoHeight ?: platformDisplayShortSide()?.takeIf { it > 0 }?.let(::snapVideoHeight)
    ?.also { resolvedVideoHeight = it }

internal fun autoQualityLabel(deviceHeight: Int?): String =
    deviceHeight?.let { "Auto (this device: ${if (it == 2160) "4K" else "${it}p"})" } ?: "Auto"
