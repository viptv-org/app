package org.viptv.app

import org.viptv.app.player.mpv.mpvNeedsToneMap
import org.viptv.app.player.mpv.mpvToneMapFilter
import kotlin.test.*

class MpvColourOutputTest {
    @Test fun pqAndHlgSourcesNeedTheToneMapChain() {
        for (transfer in listOf("pq", "hlg", "PQ", "HLG")) assertTrue(mpvNeedsToneMap(transfer), transfer)
    }

    @Test fun sdrSourcesKeepTheirOwnColours() {
        for (transfer in listOf(null, "", "bt.1886", "srgb", "linear", "gamma2.2", "gamma2.8")) assertFalse(mpvNeedsToneMap(transfer), "$transfer")
    }

    @Test fun theToneMapChainEndsInEightBitBt709Sdr() {
        assertTrue(mpvToneMapFilter.startsWith("zscale=t=linear:npl=100,"), mpvToneMapFilter)
        assertTrue("tonemap=tonemap=mobius" in mpvToneMapFilter, mpvToneMapFilter)
        assertTrue(mpvToneMapFilter.endsWith("zscale=t=bt709:m=bt709:r=tv,format=yuv420p"), mpvToneMapFilter)
    }
}
