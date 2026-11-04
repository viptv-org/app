package org.viptv.app.player.mpv

// libass draws no box unless its border style says so, and None has to say it too.
internal fun mpvSubtitleBoxOptions(boxArgb: Int?): List<Pair<String, String>> = listOf(
    "sub-back-color" to mpvSubtitleColour(boxArgb ?: 0),
    "sub-border-style" to if (boxArgb != null) "background-box" else "outline-and-shadow",
)
