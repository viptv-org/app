package org.viptv.app

import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import org.viptv.app.player.PlaybackStats

class PlaybackStatsTest {
    private val time = TestTimeSource()
    private val stats = PlaybackStats(time)

    private fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.long

    @Test fun twoStallsReportTheirCountAndSummedDurationOnce() {
        stats.stalled(false)
        time += 5_000.milliseconds
        stats.stalled(true); time += 1_200.milliseconds; stats.stalled(false)
        time += 10_000.milliseconds
        stats.stalled(true); time += 800.milliseconds; stats.stalled(false)
        stats.droppedFrames(3); stats.droppedFrames(4)
        val window = assertNotNull(stats.drain())
        assertEquals(2, window.long("rebuffer_count"))
        assertEquals(2_000, window.long("rebuffer_ms"))
        assertEquals(7, window.long("dropped_frames"))
        val next = assertNotNull(stats.drain())
        assertEquals(0, next.long("rebuffer_count"))
        assertEquals(0, next.long("rebuffer_ms"))
        assertEquals(0, next.long("dropped_frames"))
    }

    @Test fun aSessionThatNeverStallsSendsZeros() {
        stats.stalled(false)
        stats.bufferedAhead(30_000)
        stats.throughput(12_345_678)
        stats.sourceBitrate(4_500_000)
        stats.decoders("c2.android.hevc.decoder", null)
        stats.decoders(null, "c2.android.aac.decoder")
        val window = assertNotNull(stats.drain())
        assertEquals(0, window.long("rebuffer_count"))
        assertEquals(0, window.long("rebuffer_ms"))
        assertEquals(0, window.long("dropped_frames"))
        assertEquals(30.0, window.getValue("buffered_ahead_seconds").jsonPrimitive.double)
        assertEquals(12_345, window.long("throughput_kbps"))
        assertEquals(4_500, window.long("source_bitrate_kbps"))
        assertEquals("c2.android.hevc.decoder", window.getValue("video_decoder").jsonPrimitive.content)
        assertEquals("c2.android.aac.decoder", window.getValue("audio_decoder").jsonPrimitive.content)
    }

    @Test fun aStallSpanningAHeartbeatSplitsItsDurationAndCountsOnce() {
        stats.stalled(true)
        time += 3_000.milliseconds
        val first = assertNotNull(stats.drain())
        assertEquals(1, first.long("rebuffer_count"))
        assertEquals(3_000, first.long("rebuffer_ms"))
        time += 2_000.milliseconds
        stats.stalled(false)
        val second = assertNotNull(stats.drain())
        assertEquals(0, second.long("rebuffer_count"))
        assertEquals(2_000, second.long("rebuffer_ms"))
    }

    @Test fun valuesStayInsideTheServersBounds() {
        stats.decoders("x".repeat(100) + "/../file name.mkv", null)
        stats.bufferedAhead(100_000_000_000)
        stats.droppedFrames(Int.MAX_VALUE); stats.droppedFrames(Int.MAX_VALUE)
        stats.stalled(true); time += 2.hours; stats.stalled(false)
        val window = assertNotNull(stats.drain())
        val decoder = window.getValue("video_decoder").jsonPrimitive.content
        assertTrue(decoder.length <= 64 && decoder.all { it.isLetterOrDigit() || it in "._- " })
        assertEquals(86_400.0, window.getValue("buffered_ahead_seconds").jsonPrimitive.double)
        assertEquals(10_000_000, window.long("dropped_frames"))
        assertEquals(3_600_000, window.long("rebuffer_ms"))
        assertTrue(window.toString().encodeToByteArray().size <= 2048)
    }

    @Test fun aPlayerThatNeverReportedSendsNothing() {
        assertNull(stats.drain())
    }
}
