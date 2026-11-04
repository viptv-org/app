package org.viptv.app.player

internal const val CAPTION_NO_BOX = "#00000000"

internal val CAPTION_WHITE = 0xFFFFFFFF.toInt()

// Profile colours are #RRGGBB or Android #AARRGGBB; anything else keeps the renderer's own default.
internal fun captionArgb(value: String?): Int? {
    if (value == null || value.length !in setOf(7, 9) || value.firstOrNull() != '#' ||
        value.drop(1).any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val hex = value.drop(1).toLongOrNull(16) ?: return null
    return (if (value.length == 7) hex or 0xFF000000L else hex).toInt()
}

internal fun captionTextArgb(value: String?): Int = captionArgb(value) ?: CAPTION_WHITE

// A fully transparent box is None, so every renderer has to cancel its own default box instead of skipping an override.
internal fun captionBoxArgb(value: String?): Int? = captionArgb(value)?.takeUnless { it ushr 24 == 0 }

// CSS eight-digit hex is #RRGGBBAA, not Android's #AARRGGBB.
internal fun captionCssColour(argb: Int): String = buildString {
    append('#')
    listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF, argb ushr 24).forEach { channel ->
        append(channel.toString(16).uppercase().padStart(2, '0'))
    }
}
