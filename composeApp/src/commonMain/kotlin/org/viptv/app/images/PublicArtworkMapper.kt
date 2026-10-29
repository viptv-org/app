package org.viptv.app.images

import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import coil3.toUri
import io.ktor.http.Url
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter

internal class PublicArtworkMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri? =
        publicArtworkUrl(data).takeIf { it != data }?.toUri()
}

internal fun publicArtworkUrl(value: String): String {
    if (!isPublicArtwork(value)) return value
    // No background or flatten option: the caller owns the background color.
    return "$IMAGE_SERVICE?url=${value.encodeURLParameter()}&output=webp&q=90"
}

private const val IMAGE_SERVICE = "https://wsrv.nl/"

private val publicArtworkHosts = setOf("images.metahub.space", "episodes.metahub.space", "live.metahub.space",
    "image.tmdb.org", "artworks.thetvdb.com", "assets.fanart.tv", "i.imgur.com", "simkl.in")

private fun isPublicArtwork(value: String): Boolean {
    if (!value.startsWith("https://")) return false
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.host in publicArtworkHosts && url.port == 443 && url.user == null && url.password == null &&
        url.encodedQuery.isEmpty() && url.fragment.isEmpty()
}

internal fun sizedArtwork(url: String?, widthPx: Int, heightPx: Int): String? =
    if (url.isNullOrBlank()) null else serviceArtwork(url, widthPx, heightPx, blur = null) ?: url

internal fun blurredArtwork(url: String?, widthPx: Int, heightPx: Int, blur: Int): String? =
    if (url.isNullOrBlank()) null else serviceArtwork(url, widthPx, heightPx, blur)

private fun serviceArtwork(url: String, widthPx: Int, heightPx: Int, blur: Int?): String? {
    val request = serviceRequest(url) ?: return null
    // The server's own choices (blur, fit, bg, output) stay; only the size is the slot's.
    val params = linkedMapOf("url" to largestSimklUrl(request.source))
    params += request.params
    params["w"] = widthPx.coerceIn(64, 3840).toString()
    params["h"] = heightPx.coerceIn(64, 2160).toString()
    params.remove("dpr")
    params.getOrPut("fit") { "cover" }
    params.getOrPut("we") { "1" }
    params.getOrPut("output") { "webp" }
    params.getOrPut("q") { "82" }
    blur?.let { params.remove("blur"); params["blur"] = it.toString() }
    return IMAGE_SERVICE + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
}

private class ServiceRequest(val source: String, val params: Map<String, String>)

private fun serviceRequest(url: String): ServiceRequest? {
    if (!url.startsWith(IMAGE_SERVICE)) return if (isPublicArtwork(url)) ServiceRequest(url, emptyMap()) else null
    if (url.contains('#')) return null
    var source: String? = null
    val params = linkedMapOf<String, String>()
    // The server writes the source unencoded, so a source with its own query spans several `&` segments.
    for (segment in url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }) {
        val key = segment.substringBefore('=')
        val value = segment.substringAfter('=', "")
        when {
            key == "url" -> source = if (value.startsWith("https://")) value else value.decodeURLQueryComponent()
            key in serviceParameters -> params[key] = value.decodeURLQueryComponent()
            source != null -> source += "&$segment"
        }
    }
    // Already on its way to the service, so any plain https source may be re-requested at the slot's size.
    val parsed = source?.takeIf { it.startsWith("https://") }?.let { runCatching { Url(it) }.getOrNull() } ?: return null
    if (parsed.user != null || parsed.password != null) return null
    return ServiceRequest(source!!, params)
}

// Only wsrv.nl's documented parameters are the service's; every other segment belongs to the source URL.
private val serviceParameters = setOf("w", "h", "dpr", "fit", "we", "a", "fpx", "fpy", "cx", "cy", "cw", "ch",
    "precrop", "trim", "mask", "mtrim", "mbg", "flip", "flop", "ro", "bg", "cbg", "blur", "sharp", "sharpf", "sharpj",
    "con", "sat", "hue", "bri", "gam", "mod", "tint", "filter", "output", "q", "l", "af", "il", "n", "page",
    "default", "filename", "maxage", "encoding")

private fun largestSimklUrl(url: String): String {
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
