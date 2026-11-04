package org.viptv.app.player

import org.viptv.app.connection.ProfilePreferences
import kotlin.math.roundToInt

// Only validated numeric/hex values enter the video-scoped ::cue rule.
internal fun browserCaptionDeclarations(preferences: ProfilePreferences, scale: Float): String = buildString {
    val size = scale.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
    if (size != 1f) append("font-size:${size * 5}vh;")
    val text = captionTextArgb(preferences.subtitle_text_color)
    if (text != CAPTION_WHITE) append("color:${captionCssColour(text)};")
    // The browser draws its own cue box unless the rule cancels it.
    val box = captionBoxArgb(preferences.subtitle_background_color)
    append("background-color:${box?.let(::captionCssColour) ?: "transparent"};")
}

internal fun providerCueLine(scale: Float): Int {
    val size = scale.takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f
    return -1 - (4 / size).roundToInt()
}
