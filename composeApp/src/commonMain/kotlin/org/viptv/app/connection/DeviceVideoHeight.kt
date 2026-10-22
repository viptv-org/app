package org.viptv.app.connection

/**
 * The tallest video this device's display can show, in physical pixels of its
 * short side, or null when the platform cannot tell. Best match uses it as the
 * meaning of "Auto" quality (SRC-BEST-MATCH-001).
 */
internal expect fun platformDisplayShortSide(): Int?

/** Display heights snap to the standard video tiers ranking compares against. */
internal fun snapVideoHeight(pixels: Int): Int = when {
    pixels >= 2160 -> 2160
    pixels >= 1440 -> 1440
    pixels >= 1080 -> 1080
    pixels >= 720 -> 720
    else -> 480
}

private var resolvedVideoHeight: Int? = null

/** Computed once per process; an unknown answer is asked again because Android's display may not be attached yet. */
fun deviceMaxVideoHeight(): Int? = resolvedVideoHeight ?: platformDisplayShortSide()?.takeIf { it > 0 }?.let(::snapVideoHeight)
    ?.also { resolvedVideoHeight = it }

/** The "Auto" quality wording for settings: `Auto (this device: 1080p)`, or plain `Auto` when the display is unknown. */
internal fun autoQualityLabel(deviceHeight: Int?): String =
    deviceHeight?.let { "Auto (this device: ${if (it == 2160) "4K" else "${it}p"})" } ?: "Auto"
