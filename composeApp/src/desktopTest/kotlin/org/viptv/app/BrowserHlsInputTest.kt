package org.viptv.app

import org.viptv.app.player.*
import kotlin.test.*

class BrowserHlsInputTest {
    @Test fun hostHlsFormatSelectsMseForAnOpaqueSignedEndpoint() {
        val input = PlaybackInput("fixture", "https://media.example/opaque?signature=fixture",
            mimeType = playbackMimeType("hls"))
        assertTrue(input.isHlsPlaylist())
        assertTrue(PlaybackInput("fixture", input.uri, mimeType = "Application/Vnd.Apple.Mpegurl; charset=utf-8").isHlsPlaylist())
    }

    @Test fun manifestPathFallbackIgnoresFragmentsAndDoesNotInterpretQueryValuesAsFormats() {
        assertTrue(PlaybackInput("fixture", "https://media.example/INDEX.M3U8#fragment").isHlsPlaylist())
        assertFalse(PlaybackInput("fixture", "https://media.example/movie.mp4?title=playlist.m3u8").isHlsPlaylist())
        assertFalse(PlaybackInput("fixture", "https://media.example/opaque", mimeType = "video/mp4").isHlsPlaylist())
    }
}
