package org.viptv.app.features.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*

internal enum class TvSettingsSection(val label: String, val group: SettingsGroup, val icon: ImageVector) {
    Profile("Profile", SettingsGroup.Profile, Lucide.UserRound),
    SwitchProfile("Switch profile", SettingsGroup.Profile, Lucide.Users),
    Parental("Parental controls", SettingsGroup.Profile, Lucide.LockKeyhole),
    Playback("Playback", SettingsGroup.Playback, Lucide.CirclePlay),
    Subtitles("Subtitles & audio", SettingsGroup.Playback, Lucide.Captions),
    Display("Display", SettingsGroup.ThisDevice, Lucide.Monitor),
    About("About", SettingsGroup.ThisDevice, Lucide.Info),
    Account("Account", SettingsGroup.Account, Lucide.UserRound),
    Addons("Add-ons", SettingsGroup.Account, Lucide.Puzzle),
    Sources("Source integrations", SettingsGroup.Account, Lucide.Server);

    val leavesSettings: Boolean get() = this == Profile || this == SwitchProfile
}

internal fun tvSettingsGroups(): List<Pair<SettingsGroup, List<TvSettingsSection>>> =
    SettingsGroup.entries.mapNotNull { group ->
        TvSettingsSection.entries.filter { it.group == group }.takeIf { it.isNotEmpty() }?.let { group to it }
    }

internal data class TvSettingsPreferences(
    val nextEpisode: Boolean = true,
    val skipIntros: Boolean = false,
    val source: String = "Best match",
    val quality: String = "Auto",
    val hdr: String = "No preference",
    val frameRate: Boolean = true,
    val subtitleMode: String = "always",
    val subtitleLanguage: String = "English",
    val audioLanguage: String = "Original language",
    val subtitleSize: String = "Large",
    val subtitleBackground: String = "Shadow",
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBackgroundColor: String = "#00000000",
)
internal data class TvAddonInfo(val subtitle: String, val color: Long, val letter: String? = null)
