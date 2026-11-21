package org.viptv.app.features.settings

import org.viptv.app.player.languageName

internal val hdrPreferenceChoices = listOf("any" to "No preference", "prefer" to "Prefer", "avoid" to "Avoid")

internal fun hdrPreferenceLabel(value: String): String = hdrPreferenceChoices.firstOrNull { it.first == value }?.second ?: "No preference"
internal fun hdrPreferenceValue(label: String): String = hdrPreferenceChoices.firstOrNull { it.second == label }?.first ?: "any"

// The quality row shows "Auto (this device: 1080p)" while its list offers "Auto".
internal fun tvQualityChoice(quality: String): String = if (quality == "Auto" || quality == "Best available") "Auto" else quality

internal fun qualityLabel(height: Int?, deviceHeight: Int?): String =
    height?.let { if (it == 2160) "4K" else "${it}p" } ?: org.viptv.app.connection.autoQualityLabel(deviceHeight)

internal fun preferredAudioName(tag: String?): String = languageName(tag) ?: tag?.takeIf(String::isNotBlank) ?: "English"

internal val languageChoices: List<Pair<String, String>> = listOf(
    "en", "es", "pt", "fr", "de", "it", "ja", "ko", "zh", "ru", "nl", "pl", "tr", "sv", "da",
    "no", "fi", "cs", "uk", "ar", "hi", "he", "el", "hu", "ro", "th", "vi", "id", "ta", "te",
).map { it to (languageName(it) ?: it) }

internal fun languageOptionLabel(name: String, tag: String): String = "$name ($tag)"

internal fun subtitleModeChoices(preferredAudio: String?): List<Pair<String, String>> =
    listOf("always" to "Always", "foreign" to "When audio isn’t ${preferredAudioName(preferredAudio)}", "never" to "Never")

internal fun subtitleModeNote(mode: String): String? = "Signs & songs on dubs".takeIf { mode == "foreign" }

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
internal const val nextEpisodeButtonRow = "Next episode button"
internal const val nextEpisodeButtonDescription = "Shows during the credits"
internal const val skipIntrosRow = "Skip intros and recaps automatically"
internal const val skipIntrosDescription = "Skips them when you play into them. Seek into one to watch it."
internal const val skipCreditsRow = "Skip credits and outros automatically"
internal const val skipCreditsDescription = "Jumps to any scene after them. At the end of an episode, Up next takes over."
internal const val crossTitleSourceRow = "Use source choice across shows"
internal const val crossTitleSourceDescription = "Try this profile’s manually chosen source for other titles on this device."
internal const val rememberedSourceRow = "Remember source choices"
internal const val rememberedSourceDescription = "Reuses the source you picked for a title the next time you play it"

internal fun tvPlaybackRows(actualPreferencesOnly: Boolean, deviceRows: Boolean = false): List<String> = listOf(
    nextEpisodeButtonRow, autoplayNextRow, skipIntrosRow, skipCreditsRow, rememberedSourceRow, crossTitleSourceRow, "Default source", "Preferred quality", "HDR and Dolby Vision", "Match frame rate",
).filterNot { actualPreferencesOnly && it in tvPlaceholderPlaybackRows || !deviceRows && it in listOf(autoplayNextRow, crossTitleSourceRow) }
