package org.viptv.app.player

import org.viptv.app.connection.ProfilePreferences
import kotlin.math.roundToInt

/** Only validated numeric/hex values enter the video-scoped ::cue rule. */
internal fun browserCaptionDeclarations(preferences: ProfilePreferences, scale: Float): String = buildString {
    val size = scale.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
    // The browser player fills the viewport. Default preferences retain authored
    // WebVTT styling; explicit size choices use a fraction of that viewport.
    if (size != 1f) append("font-size:${size * 5}vh;")
    captionCssColor(preferences.subtitle_text_color)?.takeUnless { it == "#FFFFFFFF" }?.let {
        append("color:$it;")
    }
    captionCssColor(preferences.subtitle_background_color)?.takeUnless { it.endsWith("00") }?.let {
        append("background-color:$it;")
    }
}

/**
 * Snapped WebVTT line for provider cues that had an automatic position: the
 * last line sits about four default-size lines above the bottom (where a
 * one-line cue used to start, near 72 %) at every subtitle size, clear of the
 * toolbar. Snapped lines let the browser stack simultaneous cues and wrap long
 * ones; a percentage line turns both off (app#43).
 */
internal fun providerCueLine(scale: Float): Int {
    val size = scale.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
    return -1 - (4 / size).roundToInt()
}

/** Profile colors are #RRGGBB or Android #AARRGGBB; CSS eight-digit hex is #RRGGBBAA. */
private fun captionCssColor(value: String): String? {
    if (value.length !in setOf(7, 9) || value.firstOrNull() != '#' ||
        value.drop(1).any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val hex = value.drop(1).uppercase()
    return if (hex.length == 6) "#${hex}FF" else "#${hex.drop(2)}${hex.take(2)}"
}
