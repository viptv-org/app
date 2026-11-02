package org.viptv.app.state

import androidx.compose.runtime.Immutable
import org.viptv.app.connection.failsHardFilter
import org.viptv.app.player.PlaybackTracks
import org.viptv.app.features.settings.languageChoices
import org.viptv.engine.wire.SourceCandidate

internal const val BEST_MATCH_ATTEMPT_LIMIT = 3

// One automatic Best match start and the fallbacks it may walk. It ends with the third failed start, a presented
// frame, or the viewer's Stop, pick or Back.
@Immutable
data class BestMatchSession(
    val ranked: List<SourceCandidate>,
    val attempts: Int = 1,
    val tried: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val demoted: List<String> = emptyList(),
    val playingId: String? = null,
    val started: Boolean = false,
    val exhausted: Boolean = false,
)

// The 2 s notice between a failed start and the next attempt.
@Immutable
data class BestMatchFallback(val reason: String, val next: SourceCandidate) {
    val message: String get() = "$reason. Trying the next best source…"
}

internal fun BestMatchSession.failed(id: String, dolbyVision: Boolean): BestMatchSession = copy(
    failed = failed + id,
    tried = (tried + id).distinct(),
    demoted = if (dolbyVision) (demoted + ranked.filter { it.riskyDolbyVision() }.map { it.id }).distinct() else demoted,
)

internal fun BestMatchSession.nextTarget(): SourceCandidate? =
    ranked.firstOrNull { it.id !in tried && it.id !in demoted && !it.failsHardFilter() }

// Dolby Vision without a base layer this device can decode is the decode class the ranking cannot see yet (core#32).
internal fun SourceCandidate.riskyDolbyVision(): Boolean = "dv" in dynamic_range

internal fun failedSourcesNotice(count: Int): String =
    "$count ${if (count == 1) "source" else "sources"} couldn’t start on this device. Choose a source."

internal const val FALLBACK_REASON_FAILED = "This source could not play"
internal const val FALLBACK_REASON_UNDECODABLE = "This device can’t decode this stream"
internal const val FALLBACK_REASON_TIMEOUT = "This source did not start"

internal fun ApplicationState.sourcesBanner(): String? =
    bestMatch?.takeIf { it.exhausted }?.failed?.size?.let(::failedSourcesNotice)

internal fun ApplicationState.failedSources(): List<String> = bestMatch?.failed.orEmpty()

// No original language reaches the client yet, so only a named preference can prove a track is missing.
internal fun missingAudioNotice(preferred: String?, tracks: PlaybackTracks): String? {
    val language = preferred?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val reported = tracks.audio.mapNotNull { track -> track.language?.let(::audioLanguageTag) }.toSet()
    if (reported.isEmpty() || audioLanguageTag(language) in reported) return null
    return "This source has no ${audioLanguageName(language)} audio."
}

internal fun audioLanguageTag(value: String): String = value.trim().lowercase().substringBefore('-').substringBefore('_')

internal fun audioLanguageName(tag: String): String =
    languageChoices.firstOrNull { it.first == audioLanguageTag(tag) }?.second ?: tag
