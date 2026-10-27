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
    if (!isPublicArtwork(value)) return value
    // WebP retains the alpha channel without turning photographic art into large PNGs.
    // No background/flatten option: the caller owns the background color.
    return "$IMAGE_SERVICE?url=${value.encodeURLParameter()}&output=webp&q=90"
}

private const val IMAGE_SERVICE = "https://wsrv.nl/"

private val publicArtworkHosts = setOf("images.metahub.space", "episodes.metahub.space", "live.metahub.space",
    "image.tmdb.org", "artworks.thetvdb.com", "assets.fanart.tv", "i.imgur.com", "simkl.in")

/** A plain public URL on a known artwork host: no port, credentials, query or fragment that could carry a secret. */
private fun isPublicArtwork(value: String): Boolean {
    if (!value.startsWith("https://")) return false
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.host in publicArtworkHosts && url.port == 443 && url.user == null && url.password == null &&
        url.encodedQuery.isEmpty() && url.fragment.isEmpty()
}

/**
 * wsrv.nl request sized for the slot the art is drawn into.
 *
 * Without `w`/`h` the service hands back the full-resolution poster (often 2000 px
 * wide) for a 232 px card, so the device pays decode + upload for pixels it never
 * shows. `fit=cover` preserves the existing crop and `we` keeps small art from being
 * enlarged. Art the server already proxied through wsrv.nl is re-sized from its
 * source the same way (app#115). Art the service cannot fetch is returned unchanged.
 */
internal fun sizedArtwork(url: String?, widthPx: Int, heightPx: Int): String? =
    if (url.isNullOrBlank()) null else serviceArtwork(url, widthPx, heightPx, blur = null) ?: url

/**
 * The image service's Gaussian-blurred (sigma [blur]) rendition of [url] for a soft
 * backdrop, or null when the service cannot fetch it and the caller must blur at
 * runtime. Never an unblurred URL (app#80).
 */
internal fun blurredArtwork(url: String?, widthPx: Int, heightPx: Int, blur: Int): String? =
    if (url.isNullOrBlank()) null else serviceArtwork(url, widthPx, heightPx, blur)

private fun serviceArtwork(url: String, widthPx: Int, heightPx: Int, blur: Int?): String? {
    val source = largestSimklSize(artworkSource(url) ?: return null)
    val w = widthPx.coerceIn(64, 3840)
    val h = heightPx.coerceIn(64, 2160)
    val blurPart = blur?.let { "&blur=$it" } ?: ""
    return "$IMAGE_SERVICE?url=${source.encodeURLParameter()}&w=$w&h=$h&fit=cover&we=1&output=webp&q=82$blurPart"
}

/** The original the service should fetch: a public artwork URL, or the `url` an existing wsrv.nl request wraps. */
private fun artworkSource(url: String): String? {
    if (!url.startsWith(IMAGE_SERVICE)) return url.takeIf(::isPublicArtwork)
    // Already on its way to the service, so any plain https source may be re-requested at the slot's size.
    val inner = runCatching { Url(url).parameters["url"] }.getOrNull() ?: return null
    val parsed = runCatching { Url(inner) }.getOrNull() ?: return null
    return inner.takeIf { it.startsWith("https://") && parsed.user == null && parsed.password == null }
}

/**
 * Simkl serves each image in fixed sizes (measured 2026-10-27): posters `_s` 40, `_cm` 84,
 * `_c` 170, `_ca` 190 and `_m` 340 px wide (`_w` is a 600 x 338 landscape crop, not a
 * poster); fanart `_w` 600, `_mobile` 960 and `_medium` 1920 px; episode stills `_m` 112,
 * `_c` 210 and `_w` 600 px. Ask for the largest of the same shape and let the service
 * scale it down to the slot.
 */
private fun largestSimklSize(url: String): String {
    val match = simklImage.matchEntire(url) ?: return url
    val (folder, path, size, extension) = match.destructured
    val largest = when (folder) {
        "posters" -> "m".takeIf { size in setOf("s", "cm", "c", "ca") }
        "fanart" -> "medium".takeIf { size in setOf("w", "mobile") }
        "episodes" -> "w".takeIf { size in setOf("m", "c") }
        else -> null
    } ?: return url
    return "https://simkl.in/$folder/${path}_$largest.$extension"
}

private val simklImage = Regex("""https://simkl\.in/(posters|fanart|episodes)/([A-Za-z0-9/]+)_([a-z0-9]+)\.(webp|jpg)""")
