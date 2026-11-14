package org.viptv.app.features.sources

import org.viptv.app.connection.seasonNumber
import org.viptv.engine.wire.SourceCandidate

internal fun SourceCandidate.sourceFilterId(): String = provider_id + ":" + presentation?.source_id.orEmpty()
internal fun SourceCandidate.providerLabel(): String = presentation?.provider_name?.takeIf { it.isNotBlank() } ?: provider_id
internal fun SourceCandidate.qualityLabel(): String? = quality_height?.let { if (it == 2160L) "4K" else "${it}p" } ?: presentation?.quality

internal fun SourceCandidate.addonText(): String? = detail?.trim()?.takeIf(String::isNotEmpty)

internal class SourceTag(val label: String, val shown: (SourceCandidate) -> Boolean)

internal val SOURCE_TAGS = listOf(
    SourceTag("Dubbed") { "dubbed" in it.audio_flags },
    SourceTag("Multi audio") { "multi" in it.audio_flags || "dual" in it.audio_flags },
    SourceTag("HDR") { candidate -> candidate.dynamic_range.any { it == "hdr10" || it == "hdr10_plus" || it == "hdr" || it == "hlg" } },
    SourceTag("Dolby Vision") { "dv" in it.dynamic_range },
    SourceTag("Remux") { it.release_type == "remux" },
    SourceTag("3D") { it.three_d },
    SourceTag("CAM") { it.release_type == "cam" },
)

internal fun SourceCandidate.sourceTags(): List<String> = SOURCE_TAGS.filter { it.shown(this) }.map { it.label }

private val ADDON_SIZE = Regex("""💾\s*([0-9][0-9.,]*\s*(?:[KMGT]i?B))""", RegexOption.IGNORE_CASE)

internal fun SourceCandidate.sizeLabel(): String? = presentation?.size_bytes?.let(::sizeFromBytes)
    ?: ADDON_SIZE.find(detail.orEmpty())?.groupValues?.get(1)?.replace(',', '.')?.trim()?.takeIf(String::isNotEmpty)

internal fun sizeFromBytes(bytes: Long): String =
    if (bytes >= 1_073_741_824) "${(bytes / 107_374_182L) / 10.0} GB" else "${bytes / 1_048_576} MB"

internal enum class CacheKind { Instant, NotCached }
internal data class CacheChip(val kind: CacheKind, val service: String?)

internal fun SourceCandidate.cacheStatus(): CacheChip? = cacheChip(label, delivery)

internal fun cacheChip(label: String, delivery: String?): CacheChip? {
    val tag = DEBRID_TAG.find(label)
        ?: return if (delivery == "direct") CacheChip(CacheKind.Instant, null) else null
    val service = DEBRID_SERVICES[tag.groupValues[1]]
        ?: return if (delivery == "direct") CacheChip(CacheKind.Instant, null) else null
    val marker = tag.groupValues[2]
    val kind = if (marker.contains('+') || marker.contains('⚡')) CacheKind.Instant else CacheKind.NotCached
    return CacheChip(kind, service)
}

internal fun sourceHeading(title: org.viptv.engine.wire.Card?, detail: org.viptv.engine.wire.DetailSnapshot?): String? {
    val episode = title?.identity?.episode ?: return title?.title
    val name = detail?.item?.title ?: return title.title
    return "$name · S${episode.seasonNumber()} E${episode.episode}"
}

private val DEBRID_TAG = Regex("""\[(TB|RD|AD|PM|DL)\s*([^\]]*?)]""")
private val DEBRID_SERVICES = mapOf(
    "TB" to "TorBox", "RD" to "Real-Debrid", "AD" to "AllDebrid", "PM" to "Premiumize", "DL" to "Debrid-Link",
)
