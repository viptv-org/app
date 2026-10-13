package org.viptv.app

import org.viptv.app.player.shiftSubtitleBytes
import kotlin.test.*

class SubtitleTimelineTest {
    @Test fun cutDropsExpiredCuesAndPreservesNonUtf8BytesAndOverlap() {
        val prefix = "1\r\n00:00:01,000 --> 00:00:02,000\r\nExpired\r\n\r\n2\r\n00:00:03,000 --> 00:00:06,000\r\nCaf".encodeToByteArray()
        val bytes = prefix + byteArrayOf(0xE9.toByte())
        val result = shiftSubtitleBytes(bytes, 4_000)
        assertEquals(0xE9.toByte(), result.last())
        val ascii = result.map { (it.toInt() and 255).toChar() }.joinToString("")
        assertFalse(ascii.contains("Expired"))
        assertTrue(ascii.contains("00:00:00,000 --> 00:00:02,000"))
        assertContentEquals(bytes, shiftSubtitleBytes(bytes, 0))
    }
    @Test fun webVttStyleSettingsAndAssColumnOrderSurviveLongCuts() {
        val vtt = "WEBVTT\n\nSTYLE\n::cue { color: yellow; }\n\n00:02:00.000 --> 00:02:05.000 line:20%\nText"
        val shifted = shiftSubtitleBytes(vtt.encodeToByteArray(), 121_000).decodeToString()
        assertTrue(shifted.contains("00:00:00.000 --> 00:00:04.000 line:20%"))
        assertTrue(shifted.contains("::cue { color: yellow; }"))
        val ass = "[Events]\nFormat: End, Start, Text\nDialogue: 2:00:05.00,2:00:00.00,Long cut, text"
        val result = shiftSubtitleBytes(ass.encodeToByteArray(), 7_201_000).decodeToString()
        assertTrue(result.contains("Dialogue:0:00:04.00,0:00:00.00,Long cut, text"))
    }
}
