package org.viptv.app.features.settings

internal val hdrPreferenceChoices = listOf("any" to "No preference", "prefer" to "Prefer", "avoid" to "Avoid")

internal fun hdrPreferenceLabel(value: String): String = hdrPreferenceChoices.firstOrNull { it.first == value }?.second ?: "No preference"
internal fun hdrPreferenceValue(label: String): String = hdrPreferenceChoices.firstOrNull { it.second == label }?.first ?: "any"

internal fun qualityLabel(height: Int?, deviceHeight: Int?): String =
    height?.let { if (it == 2160) "4K" else "${it}p" } ?: org.viptv.app.connection.autoQualityLabel(deviceHeight)

internal fun preferredAudioName(tag: String?): String = when (tag?.lowercase()?.substringBefore('-')) {
    null, "" -> "English"
    "en" -> "English"; "es" -> "Spanish"; "pt" -> "Portuguese"; "fr" -> "French"; "de" -> "German"
    else -> tag
}

internal fun subtitleModeChoices(preferredAudio: String?): List<Pair<String, String>> =
    listOf("always" to "Always", "foreign" to "When audio isn’t ${preferredAudioName(preferredAudio)}", "never" to "Never")

internal fun subtitleModeLabel(mode: String, preferredAudio: String?): String =
    subtitleModeChoices(preferredAudio).firstOrNull { it.first == mode }?.second ?: "Always"

internal fun subtitleModeValue(label: String): String = when {
    label == "Never" -> "never"
    label.startsWith("When audio") -> "foreign"
    else -> "always"
}

internal val tvPlaceholderPlaybackRows = listOf("Match frame rate")

internal const val autoplayNextRow = "Autoplay next episode"
internal const val autoplayNextDescription = "Plays the next episode after a 10-second countdown. Applies to this device."

internal fun tvPlaybackRows(actualPreferencesOnly: Boolean, deviceRows: Boolean = false): List<String> = listOf(
    "Next episode button", autoplayNextRow, "Skip intros and recaps", "Default source", "Preferred quality", "HDR and Dolby Vision", "Match frame rate",
).filterNot { actualPreferencesOnly && it in tvPlaceholderPlaybackRows || !deviceRows && it == autoplayNextRow }
