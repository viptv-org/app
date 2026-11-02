package org.viptv.app

import org.viptv.app.player.mpv.*
import kotlin.test.*

class MpvColourOutputTest {
    @Test fun pqAndHlgSourcesNeedTheToneMapChain() {
        for (transfer in listOf("pq", "hlg", "PQ", "HLG")) assertTrue(mpvNeedsToneMap(transfer), transfer)
    }

    @Test fun sdrSourcesKeepTheirOwnColours() {
        for (transfer in listOf(null, "", "bt.1886", "srgb", "linear", "gamma2.2", "gamma2.8")) assertFalse(mpvNeedsToneMap(transfer), "$transfer")
    }

    @Test fun theSoftwarePathGetsNoTargetOptionsOnAnyDisplay() {
        for (hdr in listOf(false, true)) assertEquals(emptyMap(), mpvColourOptions(MpvRenderPath.Software, hdrDisplay = hdr))
    }

    @Test fun theGlPathToneMapsToSrgbOnAnSdrDisplay() {
        assertEquals(mapOf("target-prim" to "bt.709", "target-trc" to "srgb", "tone-mapping" to "spline"),
            mpvColourOptions(MpvRenderPath.Gl, hdrDisplay = false))
    }

    @Test fun onlyTheGlPathOnAnHdrDisplayHintsTheColourspace() {
        assertEquals(mapOf("target-colorspace-hint" to "yes"), mpvColourOptions(MpvRenderPath.Gl, hdrDisplay = true))
        for (path in MpvRenderPath.entries) for (hdr in listOf(false, true))
            assertEquals(path == MpvRenderPath.Gl && hdr, mpvColourOptions(path, hdr)["target-colorspace-hint"] == "yes", "$path hdr=$hdr")
    }

    @Test fun onlyTheSoftwarePathFiltersHdrSources() {
        assertEquals(mpvToneMapFilter, mpvSourceFilter(MpvRenderPath.Software, "pq"))
        assertEquals(mpvToneMapFilter, mpvSourceFilter(MpvRenderPath.Software, "hlg"))
        assertNull(mpvSourceFilter(MpvRenderPath.Software, "bt.1886"))
        assertNull(mpvSourceFilter(MpvRenderPath.Gl, "pq"))
        assertNull(mpvSourceFilter(MpvRenderPath.Gl, "bt.1886"))
    }

    @Test fun theToneMapChainEndsInEightBitBt709Sdr() {
        assertTrue(mpvToneMapFilter.startsWith("zscale=t=linear:npl=100,"), mpvToneMapFilter)
        assertTrue("tonemap=tonemap=mobius" in mpvToneMapFilter, mpvToneMapFilter)
        assertTrue(mpvToneMapFilter.endsWith("zscale=t=bt709:m=bt709:r=tv,format=yuv420p"), mpvToneMapFilter)
    }
}
