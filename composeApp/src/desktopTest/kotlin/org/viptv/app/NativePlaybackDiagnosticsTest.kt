package org.viptv.app

import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.playbackFailureSummary
import kotlin.test.*

class NativePlaybackDiagnosticsTest {
    @Test fun networkFailureKeepsReasonWithoutSignedAddressOrHeaders() {
        val uri = "https://media.example/private-path/episode.mp4?token=private-query"
        val input = PlaybackInput("attempt", uri, mapOf("Authorization" to "Bearer private-header"))
        val failure = "HTTP 403 for $uri with Bearer private-header and https://other.example/private-capability"
        val summary = playbackFailureSummary(input, "source_unavailable", failure)
        assertTrue(summary.startsWith("source_unavailable:"))
        assertTrue(summary.contains("HTTP 403"))
        for (private in listOf(uri, "private-query", "private-header", "private-path", "private-capability", "episode.mp4"))
            assertFalse(summary.contains(private))
    }
}
