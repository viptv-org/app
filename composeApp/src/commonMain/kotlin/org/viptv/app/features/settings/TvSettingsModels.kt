package org.viptv.app.features.settings

internal enum class TvSettingsSection(val label: String) {
    Account("Account"), Profiles("Profiles"), Playback("Playback"), Subtitles("Subtitles & audio"),
    Addons("Add-ons"), Display("Display"), Parental("Parental controls"), About("About"),
}

/** Optional profile preferences; absent data does not imply these defaults are saved. */
internal data class TvSettingsPreferences(
    val nextEpisode: Boolean = true,
    val skipIntros: Boolean = false,
    val source: String = "Best match",
    val quality: String = "Best available",
    val frameRate: Boolean = true,
    val startSubtitles: String = "Off",
    val subtitleLanguage: String = "English",
    val audioLanguage: String = "Original",
    val subtitleSize: String = "Large",
    val subtitleBackground: String = "Shadow",
    val subtitleColor: Long = 0xFFFFFFFF,
)
internal data class TvAddonInfo(val subtitle: String, val color: Long, val letter: String? = null)
