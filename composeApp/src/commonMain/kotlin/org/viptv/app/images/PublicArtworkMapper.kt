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
