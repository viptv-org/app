package org.viptv.app.features.settings

/** Profile `hdr_preference` values in dialog order (SRC-BEST-MATCH-001 Settings). */
internal val hdrPreferenceChoices = listOf("any" to "No preference", "prefer" to "Prefer", "avoid" to "Avoid")

internal fun hdrPreferenceLabel(value: String): String = hdrPreferenceChoices.firstOrNull { it.first == value }?.second ?: "No preference"
internal fun hdrPreferenceValue(label: String): String = hdrPreferenceChoices.firstOrNull { it.second == label }?.first ?: "any"

/** The quality row's text: a fixed cap, or `Auto` naming this device's own resolution when known. */
internal fun qualityLabel(height: Int?, deviceHeight: Int?): String =
    height?.let { if (it == 2160) "4K" else "${it}p" } ?: org.viptv.app.connection.autoQualityLabel(deviceHeight)

/** Preferred audio's display name, as subtitle mode names it; Original language or no preference reads as English. */
internal fun preferredAudioName(tag: String?): String = when (tag?.lowercase()?.substringBefore('-')) {
    null, "" -> "English"
    "en" -> "English"; "es" -> "Spanish"; "pt" -> "Portuguese"; "fr" -> "French"; "de" -> "German"
    else -> tag
}

/** PLAY-SUBTITLE-MODE-001 KMP labels, in dialog order. */
internal fun subtitleModeChoices(preferredAudio: String?): List<Pair<String, String>> =
    listOf("always" to "Always", "foreign" to "When audio isn’t ${preferredAudioName(preferredAudio)}", "never" to "Never")

internal fun subtitleModeLabel(mode: String, preferredAudio: String?): String =
    subtitleModeChoices(preferredAudio).firstOrNull { it.first == mode }?.second ?: "Always"

internal fun subtitleModeValue(label: String): String = when {
    label == "Never" -> "never"
    label.startsWith("When audio") -> "foreign"
    else -> "always"
}

/**
 * TV Playback rows the server does not store. They are placeholders in the UI
 * preview only; a real connection hides them rather than show controls with no state.
 */
internal val tvPlaceholderPlaybackRows = listOf("Next episode button", "Skip intros and recaps", "Match frame rate")

/** Stored on this device rather than with the profile (app#65): shown whenever the device record is. */
internal const val autoplayNextRow = "Autoplay next episode"
internal const val autoplayNextDescription = "Plays the next episode after a 10-second countdown. Applies to this device."

internal fun tvPlaybackRows(actualPreferencesOnly: Boolean, deviceRows: Boolean = false): List<String> = listOf(
    "Next episode button", autoplayNextRow, "Skip intros and recaps", "Default source", "Preferred quality", "HDR and Dolby Vision", "Match frame rate",
).filterNot { actualPreferencesOnly && it in tvPlaceholderPlaybackRows || !deviceRows && it == autoplayNextRow }
