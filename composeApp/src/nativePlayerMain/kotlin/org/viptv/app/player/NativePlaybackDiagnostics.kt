package org.viptv.app.player

import io.github.yuroyami.kiteplayer.PlaybackError

internal fun playbackFailureSummary(input: PlaybackInput, error: PlaybackError): String {
    val code = when (error) {
        is PlaybackError.SourceUnavailable -> "source_unavailable"
        is PlaybackError.SchemeUnsupported -> "scheme_unsupported"
        is PlaybackError.SourceStalled -> "source_stalled"
        is PlaybackError.NotMedia -> "not_media"
        is PlaybackError.NoPlayableStream -> "no_playable_stream"
        is PlaybackError.DecoderFailed -> "decoder_failed"
        is PlaybackError.ConfigurationInvalid -> "configuration_invalid"
        is PlaybackError.AudioDeviceUnavailable -> "audio_device_unavailable"
        is PlaybackError.RendererIncompatible -> "renderer_incompatible"
        is PlaybackError.RuntimeCompromised -> "runtime_compromised"
        else -> "player_internal"
    }
    var detail = when (error) {
        is PlaybackError.SourceUnavailable -> error.detail ?: error.cause?.message ?: "Media could not be opened"
        is PlaybackError.NotMedia -> error.detail ?: "Unrecognized media format"
        is PlaybackError.SchemeUnsupported -> error.detail ?: "Unsupported media scheme: ${error.scheme}"
        else -> error.message
    }
    val filename = input.uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val privateValues = listOf(input.uri, filename) + input.headers.values + input.headers.values.map { it.substringAfterLast(' ') }
    privateValues.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }.forEach {
        detail = detail.replace(it, "<private>")
    }
    detail = detail.replace(Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s\"'<>]+"), "<private-uri>")
    return "$code: ${detail.take(600)}"
}

internal expect fun reportNativePlaybackFailure(summary: String)
