package org.viptv.app.crash

import kotlinx.serialization.Serializable
import org.viptv.engine.wire.SourceCandidate

@Serializable
data class PlaybackAttempt(
    val sourceId: String? = null,
    val providerLabel: String? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val resolution: String? = null,
    val hdr: List<String> = emptyList(),
    val audioCodecs: List<String> = emptyList(),
    val delivery: String? = null,
)

@Serializable
data class CrashReport(
    val appVersion: String = "",
    val buildCommit: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val thread: String = "",
    val stack: String = "",
    val lastScreen: String? = null,
    val playback: PlaybackAttempt? = null,
    val nativeDuringPlaybackStart: Boolean = false,
)

fun playbackAttempt(source: SourceCandidate?, delivery: String?): PlaybackAttempt {
    val presentation = source?.presentation
    return PlaybackAttempt(
        sourceId = source?.id,
        providerLabel = redacted(source?.label),
        container = presentation?.container,
        videoCodec = presentation?.video_codec,
        resolution = presentation?.quality ?: source?.quality_height?.takeIf { it > 0 }?.let { "${it}p" },
        hdr = source?.dynamic_range.orEmpty(),
        audioCodecs = presentation?.audio_codecs.orEmpty(),
        delivery = delivery,
    )
}

// The report never carries URLs, tokens or headers, but a provider label is free text:
// strip scheme userinfo, query and fragment in case one smuggles them in.
internal fun redacted(value: String?): String? {
    val raw = value ?: return null
    val schemeEnd = raw.indexOf("://")
    if (schemeEnd < 0) return raw
    val authorityStart = schemeEnd + 3
    val afterAuthority = raw.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) raw.length else it }
    val host = raw.substring(authorityStart, afterAuthority).substringAfterLast('@')
    return raw.substring(0, authorityStart) + host + raw.substring(afterAuthority).substringBefore('?').substringBefore('#')
}