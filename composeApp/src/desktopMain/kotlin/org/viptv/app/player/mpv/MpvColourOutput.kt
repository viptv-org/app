package org.viptv.app.player.mpv

// mpv's colour management (--target-*, --tone-mapping) needs vo=gpu-next, which the render API cannot provide, so the
// software path hands PQ/HLG code values to Skia as sRGB and everything above ~93 nits clips. This chain tone maps it to
// BT.709/sRGB instead. SDR sources must not get it: it darkens them.
internal const val mpvToneMapFilter =
    "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=mobius:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p"

internal fun mpvNeedsToneMap(transfer: String?): Boolean = when (transfer?.lowercase()) {
    "pq", "hlg" -> true
    else -> false
}
