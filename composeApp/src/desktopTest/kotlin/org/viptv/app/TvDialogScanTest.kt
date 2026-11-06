package org.viptv.app

import java.io.File
import kotlin.test.*

// components.md §8: a TV confirmation is a TvDialogPanel, never a Material dialog drawn at phone size.
class TvDialogScanTest {
    private val root = File("src/commonMain/kotlin/org/viptv/app")
    private fun sources() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun path(file: File) = file.relativeTo(root).invariantSeparatorsPath

    private val materialDialog = Regex("""(?<![A-Za-z0-9_.])AlertDialog\(""")

    // Screens the TV layout never composes: each has its own Tv* screen or a Tv* dialog beside it.
    private val phoneAndDesktopOnly = mapOf(
        "features/entry/MobilePinScreen.kt" to "the phone PIN page; TV draws TvPinScreen",
        "features/entry/MobileProfileEdit.kt" to "the phone profile editor; TV draws TvProfileEditScreen",
        "features/entry/ServerAddressDialog.kt" to "the phone and desktop sign-in address; TV draws TvServerAddressDialog",
        "features/settings/AccountDeviceActions.kt" to "the device list's sign-out guard; TV draws TvAccountDevices",
        "features/settings/AddonSettings.kt" to "the add-on pages; TV draws TvAddonSettings",
        "features/settings/LinkedServicesSettings.kt" to "the linked-service pages; TV draws TvLinkedServices",
        "features/settings/ParentControlDialogs.kt" to "the parental dialogs; TV draws TvParentControlDialogs",
        "features/settings/PhonePreferenceDialog.kt" to "the phone preference sheet; TV draws its settings rows",
        "features/settings/ProfilePage.kt" to "the profile page; TV draws TvProfileDialogs",
        "features/settings/SettingsInformation.kt" to "the about pages; TV draws TvSettingsAbout",
        "features/settings/SourceIntegrationForm.kt" to "the source integration form; TV draws TvSourceIntegrationForm",
        "features/settings/VodMatchesSettings.kt" to "the VOD match pages; TV draws TvVodMatchesSettings",
        "features/settings/XtreamConnectionsSettings.kt" to "the Xtream pages; TV draws TvXtreamConnections",
    )

    // Every surface that can draw on TV and owns a confirmation names the panel it uses.
    private val tvPanels = mapOf(
        "components/ListRemoval.kt" to "TvConfirmDialog(",
        "features/library/TvLibraryScreen.kt" to "television = true",
        "features/details/EpisodeJump.kt" to "TvDialogPanel(",
        "features/details/EpisodeUpcomingDialog.kt" to "TvNoticeDialog(",
        "features/details/TvDetailsScreen.kt" to "TvNoticeDialog(",
        "features/entry/DevicePairingRequest.kt" to "TvDialogPanel(",
        "features/entry/ProfilePreferenceGuidance.kt" to "TvConfirmDialog(",
        "features/entry/ProfilesScreen.kt" to "TvDialogPanel(",
        "features/entry/TvPinScreen.kt" to "TvNoticeDialog(",
        "features/history/HistoryRow.kt" to "TvDialogPanel(",
        "features/history/TvHistoryScreen.kt" to "TvConfirmDialog(",
        "features/settings/ParentTitlesSettings.kt" to "TvConfirmDialog(",
        "features/settings/TvSettingsDialogs.kt" to "TvConfirmDialog(",
        "features/settings/TvSettingsScreen.kt" to "TvNoticeDialog(",
    )

    @Test fun noTvScreenDrawsAMaterialDialog() {
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        val found = sources().filter { it.name.startsWith("Tv") }
            .filter { materialDialog.containsMatchIn(it.readText()) }.map(::path)
        assertEquals(emptyList(), found, "a TV screen draws TvDialogPanel, never a Material AlertDialog")
    }

    @Test fun everyMaterialDialogIsOnAPhoneOrDesktopSurface() {
        val found = sources().filter { materialDialog.containsMatchIn(it.readText()) }
            .filter { !it.readText().contains("television") }.map(::path).toSet()
        assertEquals(phoneAndDesktopOnly.keys, found,
            "a Material dialog lives only where TV never draws: route a TV-reachable one through TvDialogPanel, or add it here with the screen that keeps it off TV")
    }

    @Test fun everySurfaceTvCanReachDrawsTheSharedPanel() {
        for ((name, panel) in tvPanels) assertTrue(File(root, name).readText().contains(panel), "$name draws $panel")
    }

    @Test fun thePanelIsTheOnlyTvDialogContainer() {
        val panel = File(root, "features/settings/TvDialogPanel.kt").readText()
        for (piece in listOf("internal fun TvDialogPanel(", "internal fun TvConfirmDialog(", "internal fun TvNoticeDialog("))
            assertTrue(panel.contains(piece), "TvDialogPanel.kt holds $piece")
        val ownDialogs = sources().filter { it.name.startsWith("Tv") && it.name != "TvDialogPanel.kt" }
            .filter { Regex("""\bDialog\(""").containsMatchIn(it.readText()) }.map(::path)
        assertEquals(emptyList(), ownDialogs, "a TV panel is TvDialogPanel, not a second dialog of its own")
    }
}