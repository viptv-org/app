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
        providerLabel = scrub(source?.label),
        container = presentation?.container,
        videoCodec = presentation?.video_codec,
        resolution = presentation?.quality ?: source?.quality_height?.takeIf { it > 0 }?.let { "${it}p" },
        hdr = source?.dynamic_range.orEmpty(),
        audioCodecs = presentation?.audio_codecs.orEmpty(),
        delivery = delivery,
    )
}

private val URL = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>()]+""")
private val TOKEN = Regex("""[A-Za-z0-9+/=_-]{32,}""")

// The report never carries URLs, tokens or headers, but stack traces and provider labels are free
// text, so every scheme://… is reduced to scheme://host and any long hex/base64-looking run is redacted.
fun scrub(value: String?): String? {
    val raw = value ?: return null
    val urls = URL.replace(raw) { match ->
        val url = match.value
        val authorityStart = url.indexOf("://") + 3
        val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) url.length else it }
        url.substring(0, authorityStart) + url.substring(authorityStart, authorityEnd).substringAfterLast('@')
    }
    return TOKEN.replace(urls, "<redacted>")
}

fun CrashReport.sanitized(): CrashReport = copy(
    appVersion = scrub(appVersion) ?: "",
    buildCommit = scrub(buildCommit) ?: "",
    deviceModel = scrub(deviceModel) ?: "",
    androidVersion = scrub(androidVersion) ?: "",
    thread = scrub(thread) ?: "",
    stack = scrub(stack) ?: "",
    lastScreen = scrub(lastScreen),
    playback = playback?.sanitized(),
)

fun PlaybackAttempt.sanitized(): PlaybackAttempt = copy(
    sourceId = scrub(sourceId),
    providerLabel = scrub(providerLabel),
    container = scrub(container),
    videoCodec = scrub(videoCodec),
    resolution = scrub(resolution),
    delivery = scrub(delivery),
    hdr = hdr.mapNotNull { scrub(it) },
    audioCodecs = audioCodecs.mapNotNull { scrub(it) },
)