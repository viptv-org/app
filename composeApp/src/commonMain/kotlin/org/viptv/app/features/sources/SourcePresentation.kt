package org.viptv.app.features.sources

import org.viptv.engine.wire.SourceCandidate

internal fun SourceCandidate.sourceFilterId(): String = provider_id + ":" + presentation?.source_id.orEmpty()
internal fun SourceCandidate.providerLabel(): String = presentation?.provider_name?.takeIf { it.isNotBlank() } ?: provider_id
internal fun SourceCandidate.qualityLabel(): String? = quality_height?.let { if (it == 2160L) "4K" else "${it}p" } ?: presentation?.quality
internal fun SourceCandidate.formatLabel(): String? = presentation?.let { it.container ?: it.delivery_format?.takeIf { format -> format.equals("hls", true) }?.uppercase() }

internal fun SourceCandidate.releaseTypeLabel(): String? = when (release_type) {
    "web_dl" -> "WEB-DL"; "webrip" -> "WEBRip"; "bluray" -> "BluRay"; "remux" -> "Remux"
    "hdtv" -> "HDTV"; "dvd" -> "DVD"; "other_rip" -> "Rip"; "cam" -> "CAM"; else -> null
}

internal fun SourceCandidate.dynamicRangeLabel(): String? = when {
    "dv" in dynamic_range -> "DV"
    "hdr10_plus" in dynamic_range -> "HDR10+"
    dynamic_range.any { it == "hdr10" || it == "hdr" || it == "hlg" } -> "HDR"
    else -> null
}

internal fun SourceCandidate.audioFlagLabel(): String? = when {
    "dual" in audio_flags -> "Dual audio"
    "multi" in audio_flags -> "Multi audio"
    "dubbed" in audio_flags -> "Dubbed"
    else -> null
}

internal fun SourceCandidate.qualitySubline(): String? = dynamicRangeLabel() ?: releaseTypeLabel()

internal fun SourceCandidate.sizeLabel(): String? = presentation?.let { value ->
    value.size_label ?: value.size_bytes?.let { bytes ->
        if (bytes >= 1_073_741_824) "${(bytes / 107_374_182L) / 10.0} GB" else "${bytes / 1_048_576} MB"
    }
}

internal fun SourceCandidate.codecLabel(): String? = presentation?.video_codec?.takeIf { it.isNotBlank() }?.let { codec ->
    bitDepthLabel()?.takeUnless { depth -> codec.contains(depth, ignoreCase = true) }?.let { "$codec $it" } ?: codec
}

internal fun SourceCandidate.trackerLabel(): String? = parseTracker(detail)

internal fun SourceCandidate.seasonPack(): Boolean = detail?.let { SEASON_PACK.containsMatchIn(it) } == true

internal enum class CacheKind { Instant, NotCached }
internal data class CacheChip(val kind: CacheKind, val service: String?)
internal data class LanguageChip(val label: String, val dashed: Boolean)

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

internal fun SourceCandidate.releaseGroupLabel(): String = parseReleaseGroup(detail) ?: providerLabel()

internal fun SourceCandidate.audioChips(): List<LanguageChip>? {
    val codes = languageTags(audio_languages)
    val dubbed = "dubbed" in audio_flags
    val dual = "dual" in audio_flags
    if (codes.isEmpty() && !dubbed && !dual) return null
    val chips = mutableListOf<LanguageChip>()
    when {
        dubbed && "EN" in codes -> chips += LanguageChip("EN dub", dashed = false)
        dubbed -> chips += LanguageChip("EN dub", dashed = true)
        "EN" in codes -> chips += LanguageChip("EN", dashed = false)
    }
    codes.filter { it != "EN" }.forEach { chips += LanguageChip(it, dashed = false) }
    if (dual && chips.none { it.label != "EN dub" }) chips += LanguageChip("JA", dashed = true)
    return chips.ifEmpty { null }
}

internal fun SourceCandidate.subtitleChips(): List<LanguageChip>? {
    val codes = languageTags(subtitle_languages)
    if (codes.isNotEmpty()) return codes.map { LanguageChip(it, dashed = false) }
    return if (subbed) listOf(LanguageChip("Subs", dashed = true)) else null
}

internal fun SourceCandidate.factItems(addon: String? = null): List<String> = listOfNotNull(
    sizeLabel(),
    codecLabel(),
    seeders?.let { "↑$it" },
    trackerLabel(),
    addon?.takeIf { it.isNotBlank() } ?: presentation?.provider_name?.takeIf { it.isNotBlank() },
    SEASON_PACK_LABEL.takeIf { seasonPack() },
).distinct()

internal fun SourceCandidate.factBadges(): List<String> = listOfNotNull(
    releaseTypeLabel(),
    dynamicRangeLabel(),
    audioFlagLabel(),
    languageBadge(audio_languages),
    subtitleBadge(),
    "3D".takeIf { three_d },
)

private val LEADING_GROUP = Regex("""^\[\s*([^\[\]]+?)\s*]""")
private val TRAILING_EXTENSION = Regex("""\.\w{2,5}$""")
private val TRAILING_GROUP = Regex("""-([A-Za-z][A-Za-z0-9._-]*)$""")
private val BIT_DEPTH = Regex("""(?i)\b10[ ._-]*bit""")
private val SEASON_PACK = Regex(
    """(?i)\[batch]|\(batch\)|"batch"|\bs\d{1,2}\s*[-:.]?\s*complete\b|\bseason\s*\d{1,2}\s*-\s*\d{1,2}\b|\bs\d{1,2}e\d{1,3}\s*-\s*e?\d{1,3}\b|\bseason\s*(?:pack|full|complete)\b"""
)
internal const val SEASON_PACK_LABEL = "Season pack"
private val DEBRID_TAG = Regex("""\[(TB|RD|AD|PM|DL)\s*([^\]]*?)]""")
private val DEBRID_SERVICES = mapOf(
    "TB" to "TorBox", "RD" to "Real-Debrid", "AD" to "AllDebrid", "PM" to "Premiumize", "DL" to "Debrid-Link",
)

internal fun parseReleaseGroup(text: String?): String? {
    val lines = text.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
    for (line in lines) LEADING_GROUP.find(line)?.let { return it.groupValues[1].trim().takeIf(String::isNotBlank) }
    val last = lines.lastOrNull() ?: return null
    return TRAILING_GROUP.find(last.replace(TRAILING_EXTENSION, ""))?.let { it.groupValues[1].trim().takeIf(String::isNotBlank) }
}

internal fun parseTracker(text: String?): String? =
    text.orEmpty().lineSequence().firstOrNull { '⚙' in it }?.let { line ->
        line.split(' ', '|', '\t').lastOrNull { w -> w.isNotBlank() && '⚙' !in w }?.trim()
    }

internal fun SourceCandidate.bitDepthLabel(): String? = detail?.takeIf { BIT_DEPTH.containsMatchIn(it) }?.let { "10-bit" }

private const val LANGUAGE_BADGE_LIMIT = 3
private fun languageTags(tags: List<String>): List<String> = tags.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
private fun languageBadge(tags: List<String>): String? = languageTags(tags).takeIf { it.isNotEmpty() }?.let { shown ->
    shown.take(LANGUAGE_BADGE_LIMIT).joinToString(" / ") + if (shown.size > LANGUAGE_BADGE_LIMIT) " +${shown.size - LANGUAGE_BADGE_LIMIT}" else ""
}
private fun SourceCandidate.subtitleBadge(): String? = languageBadge(subtitle_languages)?.let { "Subs $it" } ?: "Subs".takeIf { subbed }
internal fun SourceCandidate.releaseDetail(): String? = detail?.takeIf {
    it.isNotBlank() && it.trim() != label.trim() && it.trim() != technicalDetail()?.trim()
}
internal fun SourceCandidate.descriptionText(): String? =
    listOfNotNull(releaseDetail(), technicalDetail()).distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")

internal fun SourceCandidate.technicalDetail(): String? {
    val fallback = detail?.takeUnless { it.trim() == qualityLabel() }
    val value = presentation ?: return fallback
    val parts = listOfNotNull(value.container, value.video_codec, value.audio_codecs.takeIf { it.isNotEmpty() }?.joinToString(" / "),
        value.size_label ?: value.size_bytes?.let { bytes ->
            if (bytes >= 1_073_741_824) "${(bytes / 107_374_182L) / 10.0} GB" else "${bytes / 1_048_576} MB"
        })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: fallback
}

internal fun sourceHeading(title: org.viptv.engine.wire.Card?, detail: org.viptv.engine.wire.DetailSnapshot?): String? {
    val episode = title?.identity?.episode ?: return title?.title
    val name = detail?.item?.title ?: return title.title
    return "$name · S${episode.season ?: 1} E${episode.episode}"
}