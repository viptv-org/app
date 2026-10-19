package org.viptv.app.images

import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import coil3.toUri
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

/** Shared by Android, desktop and browser; private/provider URLs stay on their own transport. */
internal class PublicArtworkMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? =
        publicArtworkUrl(data).takeIf { it != data }?.toUri()
}

internal fun publicArtworkUrl(value: String): String {
    if (!value.startsWith("https://")) return value
    val url = runCatching { Url(value) }.getOrNull() ?: return value
    if (url.host !in publicArtworkHosts || url.port != 443 || url.user != null || url.password != null ||
        url.encodedQuery.isNotEmpty() || url.fragment.isNotEmpty()) return value
    // WebP retains the alpha channel without turning photographic art into large PNGs.
    // No background/flatten option: the caller owns the background color.
    return "https://wsrv.nl/?url=${value.encodeURLParameter()}&output=webp&q=90"
}

private val publicArtworkHosts = setOf("images.metahub.space", "episodes.metahub.space", "live.metahub.space",
    "image.tmdb.org", "artworks.thetvdb.com", "assets.fanart.tv", "i.imgur.com", "simkl.in")

/**
 * wsrv.nl request sized for the slot the art is drawn into.
 *
 * Without `w`/`h` the service hands back the full-resolution poster (often 2000 px
 * wide) for a 232 px card, so the device pays decode + upload for pixels it never
 * shows. `fit=cover` preserves the existing crop, `we` keeps small art from being
 * enlarged, and [blur] asks wsrv for a Gaussian blur (sigma) so a backdrop can be
 * composed from an already-blurred bitmap instead of a runtime blur pass.
 */
internal fun sizedArtwork(url: String?, widthPx: Int, heightPx: Int, blur: Int? = null): String? {
    if (url == null) return null
    if (url.isBlank()) return null
    if (!url.startsWith("https://")) return url
    if (url.startsWith("https://wsrv.nl/")) return url
    val url2 = runCatching { Url(url) }.getOrNull() ?: return url
    if (url2.host !in publicArtworkHosts || url2.port != 443 || url2.user != null || url2.password != null ||
        url2.encodedQuery.isNotEmpty() || url2.fragment.isNotEmpty()) return url
    val w = widthPx.coerceIn(64, 3840)
    val h = heightPx.coerceIn(64, 2160)
    val blurPart = blur?.let { "&blur=$it" } ?: ""
    return "https://wsrv.nl/?url=${url.encodeURLParameter()}&w=$w&h=$h&fit=cover&we=1&output=webp&q=82$blurPart"
}
