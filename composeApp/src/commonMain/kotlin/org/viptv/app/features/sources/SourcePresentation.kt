package org.viptv.app.features.sources

import org.viptv.engine.wire.SourceCandidate

internal fun SourceCandidate.sourceFilterId(): String = provider_id + ":" + presentation?.source_id.orEmpty()
internal fun SourceCandidate.providerLabel(): String = presentation?.provider_name?.takeIf { it.isNotBlank() } ?: provider_id
internal fun SourceCandidate.qualityLabel(): String? = quality_height?.let { if (it == 2160L) "4K" else "${it}p" } ?: presentation?.quality
internal fun SourceCandidate.formatLabel(): String? = presentation?.let { it.container ?: it.delivery_format?.takeIf { format -> format.equals("hls", true) }?.uppercase() }
/**
 * Short release facts shown beside the quality badge (SRC-BEST-MATCH-001), in
 * display order: release type, dynamic range, audio flag, 3D. Unknown facts are
 * left out rather than guessed.
 */
internal fun SourceCandidate.factBadges(): List<String> = listOfNotNull(
    when (release_type) {
        "web_dl" -> "WEB-DL"; "webrip" -> "WEBRip"; "bluray" -> "BluRay"; "remux" -> "Remux"
        "hdtv" -> "HDTV"; "dvd" -> "DVD"; "other_rip" -> "Rip"; "cam" -> "CAM"; else -> null
    },
    when {
        "dv" in dynamic_range -> "DV"
        "hdr10_plus" in dynamic_range -> "HDR10+"
        dynamic_range.any { it == "hdr10" || it == "hdr" || it == "hlg" } -> "HDR"
        else -> null
    },
    when {
        "dual" in audio_flags -> "Dual audio"
        "multi" in audio_flags -> "Multi audio"
        "dubbed" in audio_flags -> "Dubbed"
        else -> null
    },
    "3D".takeIf { three_d },
)
/** A provider's release title/description remains readable even after technical parsing succeeds. */
internal fun SourceCandidate.releaseDetail(): String? = detail?.takeIf {
    it.isNotBlank() && it.trim() != label.trim() && it.trim() != technicalDetail()?.trim()
}
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
