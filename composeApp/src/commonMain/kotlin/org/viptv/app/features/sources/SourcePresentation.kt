package org.viptv.app.features.sources

import org.viptv.engine.wire.SourceCandidate

internal fun SourceCandidate.sourceFilterId(): String = provider_id + ":" + presentation?.source_id.orEmpty()
internal fun SourceCandidate.providerLabel(): String = presentation?.provider_name?.takeIf { it.isNotBlank() } ?: provider_id
internal fun SourceCandidate.qualityLabel(): String? = quality_height?.let { if (it == 2160L) "4K" else "${it}p" } ?: presentation?.quality
internal fun SourceCandidate.formatLabel(): String? = presentation?.let { it.container ?: it.delivery_format?.takeIf { format -> format.equals("hls", true) }?.uppercase() }
internal fun SourceCandidate.technicalDetail(): String? {
    val value = presentation ?: return detail
    val parts = listOfNotNull(value.container, value.video_codec, value.audio_codecs.takeIf { it.isNotEmpty() }?.joinToString(" / "),
        value.size_label ?: value.size_bytes?.let { bytes ->
            if (bytes >= 1_073_741_824) "${(bytes / 107_374_182L) / 10.0} GB" else "${bytes / 1_048_576} MB"
        })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: detail
}

internal fun sourceHeading(title: org.viptv.engine.wire.Card?, detail: org.viptv.engine.wire.DetailSnapshot?): String? {
    val episode = title?.identity?.episode ?: return title?.title
    val name = detail?.item?.title ?: return title.title
    return "$name · S${episode.season ?: 1} E${episode.episode}"
}
