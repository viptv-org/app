package org.viptv.app.player

import org.viptv.app.connection.subtitleMode
import org.viptv.engine.wire.SubtitleCandidate

internal fun preferredProviderSubtitle(input: PlaybackInput, candidates: List<SubtitleCandidate>): SubtitleCandidate? {
    if (input.preferences.subtitleMode == "never") return null
    val language = input.preferences.preferred_subtitle_language ?: return null
    val desired = languageTag(language) ?: return null
    return candidates.firstOrNull { languageTag(it.language) == desired } ?: candidates.firstOrNull { sameLanguage(it.language, language) }
}
