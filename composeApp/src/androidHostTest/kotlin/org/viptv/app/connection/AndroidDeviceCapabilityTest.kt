package org.viptv.app.connection

import android.media.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidDeviceCapabilityTest {
    @Test fun videoCodecsReportHardwareHeightsAndMissingDecodersAsUnsupported() {
        val facts = listOf(
            DecoderFact("video/avc", hardware = true, maxHeight = 1080),
            DecoderFact("video/hevc", hardware = true, maxHeight = 2160),
            DecoderFact("video/hevc", hardware = false, maxHeight = null),
        )
        assertEquals(listOf(
            VideoCodecSupport("avc", DecodeSupport.SUPPORTED, 1080),
            VideoCodecSupport("hevc", DecodeSupport.SUPPORTED, 2160),
            VideoCodecSupport("av1", DecodeSupport.UNSUPPORTED),
            VideoCodecSupport("vp9", DecodeSupport.UNSUPPORTED),
        ), videoCodecSupports(facts))
        assertEquals(VideoCodecSupport("avc", DecodeSupport.SUPPORTED), videoCodecSupports(
            listOf(DecoderFact("video/avc", hardware = false, maxHeight = null))).first())
    }

    @Test fun audioCodecsUseDecodersPassthroughProfilesAndUnknownWithoutThem() {
        val facts = listOf(DecoderFact("audio/eac3", hardware = false, maxHeight = null))
        val passthrough = setOf(AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_DTS)
        assertEquals(listOf(
            AudioCodecSupport("ac3", DecodeSupport.SUPPORTED),
            AudioCodecSupport("eac3", DecodeSupport.SUPPORTED),
            AudioCodecSupport("truehd", DecodeSupport.UNSUPPORTED),
            AudioCodecSupport("dts", DecodeSupport.SUPPORTED),
        ), audioCodecSupports(facts, passthrough))
        assertEquals(listOf(
            AudioCodecSupport("ac3", DecodeSupport.UNKNOWN),
            AudioCodecSupport("eac3", DecodeSupport.SUPPORTED),
            AudioCodecSupport("truehd", DecodeSupport.UNKNOWN),
            AudioCodecSupport("dts", DecodeSupport.UNKNOWN),
        ), audioCodecSupports(facts, null))
    }

    @Test fun aDecoderFactWithoutAHeightLeavesTheHeightUnknown() {
        assertEquals(VideoCodecSupport("hevc", DecodeSupport.SUPPORTED, null),
            videoCodecSupports(listOf(DecoderFact("video/hevc", hardware = true, maxHeight = null)))[1])
        assertNull(videoCodecSupports(emptyList()).single { it.codec == "av1" }.maxHeight)
    }
}
