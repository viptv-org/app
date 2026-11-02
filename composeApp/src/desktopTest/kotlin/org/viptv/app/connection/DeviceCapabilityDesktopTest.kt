package org.viptv.app.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceCapabilityDesktopTest {
    @Test fun mpvCodecNamesMapToWireVideoCodecs() {
        assertEquals(listOf(
            VideoCodecSupport("avc", DecodeSupport.SUPPORTED),
            VideoCodecSupport("hevc", DecodeSupport.SUPPORTED),
            VideoCodecSupport("av1", DecodeSupport.SUPPORTED),
            VideoCodecSupport("vp9", DecodeSupport.SUPPORTED),
        ), desktopCapability(setOf("h264", "hevc", "av1", "vp9")).videoCodecs)
    }

    @Test fun aCodecWithoutAMpvDecoderReportsUnsupported() {
        assertEquals(listOf(
            VideoCodecSupport("avc", DecodeSupport.UNSUPPORTED),
            VideoCodecSupport("hevc", DecodeSupport.UNSUPPORTED),
            VideoCodecSupport("av1", DecodeSupport.SUPPORTED),
            VideoCodecSupport("vp9", DecodeSupport.UNSUPPORTED),
        ), desktopCapability(setOf("av1")).videoCodecs)
    }

    @Test fun hardwareHeightAndDolbyVisionStayUnknown() {
        val capability = desktopCapability(setOf("h264", "hevc", "av1", "vp9"))
        assertNull(capability.maxHeight)
        assertEquals(DecodeSupport.UNKNOWN, capability.dolbyVision)
        assertEquals(emptyList(), capability.displayHdr)
        capability.videoCodecs.forEach { assertNull(it.maxHeight) }
    }
}
