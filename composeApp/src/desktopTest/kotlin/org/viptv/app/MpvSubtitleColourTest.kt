package org.viptv.app

import org.viptv.app.player.mpv.mpvSubtitleColour
import kotlin.test.Test
import kotlin.test.assertEquals

class MpvSubtitleColourTest {
    @Test fun profileColoursArePassedToMpvAsAarrggbb() {
        // mpv(1): alpha comes first, "#AARRGGBB".
        assertEquals("#FFFFC107", mpvSubtitleColour(0xFFFFC107.toInt()))
        assertEquals("#CC0000FF", mpvSubtitleColour(0xCC0000FF.toInt()))
        assertEquals("#80FF0000", mpvSubtitleColour(0x80FF0000.toInt()))
        assertEquals("#FF000000", mpvSubtitleColour(0xFF000000.toInt()))
        assertEquals("#FFFFFFFF", mpvSubtitleColour(0xFFFFFFFF.toInt()))
    }
}