package org.viptv.app.player.mpv

// SDR sources must not get this chain: it darkens them.
internal const val mpvToneMapFilter =
    "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=tonemap=mobius:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p"

internal fun mpvNeedsToneMap(transfer: String?): Boolean = when (transfer?.lowercase()) {
    "pq", "hlg" -> true
    else -> false
}
