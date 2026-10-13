package org.viptv.app.player

import org.viptv.engine.wire.SubtitleCandidate

internal expect fun canonicalLanguageTag(value: String): String

internal fun preferredProviderSubtitle(input: PlaybackInput, candidates: List<SubtitleCandidate>): SubtitleCandidate? {
    if (!input.preferences.subtitles_enabled) return null
    val language = input.preferences.preferred_subtitle_language ?: return null
    val desired = canonicalLanguageTag(language)
    return candidates.firstOrNull { canonicalLanguageTag(it.language) == desired }
        ?: candidates.firstOrNull { canonicalLanguageTag(it.language).substringBefore('-') == desired.substringBefore('-') }
}
