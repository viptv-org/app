package org.viptv.app.features.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*

internal enum class SettingsGroup(val label: String) {
    Profile("Profile"),
    Playback("Playback"),
    ThisDevice("This device"),
    Account("Account");

    val rows: List<SettingsRow> get() = SettingsRow.entries.filter { it.group == this }
}

internal enum class SettingsRow(val group: SettingsGroup, val key: String, val label: String, val icon: ImageVector) {
    Profile(SettingsGroup.Profile, "Profile", "Profile", Lucide.UserRound),
    LinkedServices(SettingsGroup.Profile, "Linked services", "Linked services", Lucide.Link),
    ParentalControls(SettingsGroup.Profile, "Parental controls", "Parental controls", Lucide.LockKeyhole),
    ImportFromStremio(SettingsGroup.Profile, stremioImportSection, "Import from Stremio", Lucide.Import),

    Playback(SettingsGroup.Playback, "Playback", "Playback", Lucide.CirclePlay),
    SubtitlesAudio(SettingsGroup.Playback, "Subtitles & audio", "Subtitles & audio", Lucide.Captions),

    Display(SettingsGroup.ThisDevice, "Display", "Display", Lucide.Monitor),
    WatchOnTv(SettingsGroup.ThisDevice, "Watch on TV", "Watch on TV", Lucide.Airplay),
    About(SettingsGroup.ThisDevice, "About", "About", Lucide.Info),
    Help(SettingsGroup.ThisDevice, "Help", "Help", Lucide.Info),

    Account(SettingsGroup.Account, "Account", "Account", Lucide.UserRound),
    Password(SettingsGroup.Account, "Password", "Password", Lucide.KeyRound),
    HouseholdPin(SettingsGroup.Account, "Household PIN", "Household PIN", Lucide.Lock),
    Devices(SettingsGroup.Account, "Devices", "Devices & sessions", Lucide.Monitor),
    AddDevice(SettingsGroup.Account, "Add a device", "Add a device", Lucide.Smartphone),
    Addons(SettingsGroup.Account, "Add-ons", "Add-ons", Lucide.Puzzle),
    SourceIntegrations(SettingsGroup.Account, "Source integrations", "Source integrations", Lucide.Server),
    SignOut(SettingsGroup.Account, "Sign out", "Sign out", Lucide.LogOut),
}

internal data class SettingsSection(val group: SettingsGroup, val rows: List<SettingsRow>) {
    val key: String get() = "group:${group.name}"
}

internal fun settingsSections(available: Set<SettingsRow>): List<SettingsSection> =
    SettingsGroup.entries.mapNotNull { group ->
        group.rows.filter { it in available }.takeIf { it.isNotEmpty() }?.let { SettingsSection(group, it) }
    }

internal fun availableRows(parental: Boolean, stremioImport: Boolean, playback: Boolean, account: Boolean,
    devices: Boolean, addDevice: Boolean, watchOnTv: Boolean, information: Boolean,
    householdPin: Boolean = false, sourceIntegrations: Boolean = false, linkedServices: Boolean = false): Set<SettingsRow> = buildSet {
    add(SettingsRow.Profile)
    if (linkedServices) add(SettingsRow.LinkedServices)
    if (parental) add(SettingsRow.ParentalControls)
    if (stremioImport) add(SettingsRow.ImportFromStremio)
    if (playback) { add(SettingsRow.Playback); add(SettingsRow.SubtitlesAudio) }
    if (watchOnTv) add(SettingsRow.WatchOnTv)
    if (information) { add(SettingsRow.About); add(SettingsRow.Help) }
    if (account) add(SettingsRow.Account)
    if (householdPin) add(SettingsRow.HouseholdPin)
    if (devices) add(SettingsRow.Devices)
    if (addDevice) add(SettingsRow.AddDevice)
    add(SettingsRow.Addons)
    if (sourceIntegrations) add(SettingsRow.SourceIntegrations)
    add(SettingsRow.SignOut)
}

internal fun signOutLabel(local: Boolean) = if (local) "Change connection" else "Sign out"

internal enum class SettingsLayout(val groupLabelSize: TextUnit, val groupLabelTracking: Float,
    val groupLabelHorizontal: Dp, val groupLabelAbove: Dp, val groupLabelBelow: Dp) {
    Phone(11.sp, .08f, 16.dp, groupLabelAbove = 2.dp, groupLabelBelow = 8.dp),
    Tablet(11.sp, .08f, 16.dp, groupLabelAbove = 16.dp, groupLabelBelow = 12.dp),
    Desktop(12.sp, .06f, 12.dp, groupLabelAbove = 8.dp, groupLabelBelow = 8.dp),
    Web(12.sp, 0f, 16.dp, groupLabelAbove = 12.dp, groupLabelBelow = 12.dp),
}
