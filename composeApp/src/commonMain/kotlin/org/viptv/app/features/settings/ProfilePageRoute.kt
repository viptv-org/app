package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.*

@Composable
internal fun ProfilePageRoute(state: ApplicationState, controller: ApplicationController, layout: SettingsLayout) {
    val values = profilePageValues(state)
    LaunchedEffect(state.selectedProfile, state.parentTitles == null) { if (state.selectedProfile != null) controller.refreshProfilePage() }
    ProfileSettingsPage(values, state.busy, state.error, state.profilePageSaved,
        profilePageActions(controller, values, parentalControls = { controller.showSettingsPage(org.viptv.app.state.SettingsPage("Parental controls")) },
            language = { controller.showSettingsPage(org.viptv.app.state.SettingsPage("Subtitles & audio")) }),
        layout)
}

@Composable
internal fun TvProfilePageRoute(state: ApplicationState, controller: ApplicationController, scale: Float,
    first: androidx.compose.ui.focus.FocusRequester, sidebar: androidx.compose.ui.focus.FocusRequester,
    openSection: (TvSettingsSection) -> Unit) {
    val values = profilePageValues(state)
    LaunchedEffect(state.selectedProfile, state.parentTitles == null) { if (state.selectedProfile != null) controller.refreshProfilePage() }
    TvProfileSettingsPage(values, state.busy, state.error, state.profilePageSaved,
        profilePageActions(controller, values, parentalControls = { openSection(TvSettingsSection.Parental) },
            language = { openSection(TvSettingsSection.Subtitles) }),
        scale, first, sidebar, openParental = { openSection(TvSettingsSection.Parental) })
}

internal fun profilePageValues(state: ApplicationState): ProfilePageValues? {
    val id = state.selectedProfile ?: return null
    val profile = state.profiles.firstOrNull { it.id == id } ?: return null
    val server = state.profileManagement?.profiles?.firstOrNull { it.id == id }
    val parent = state.profileManagement?.parent
    val preferences: ProfilePreferences? = state.preferences?.preferences
    return ProfilePageValues(profile = profile, local = state.localMode, kids = server?.kids == true, maxAge = server?.max_age,
        approvedTitles = state.profileApprovals, householdPinConfigured = parent?.pin_configured == true,
        householdUnlocked = parent?.unlocked == true, audioLanguage = preferences?.preferred_audio_language,
        subtitleLanguage = preferences?.preferred_subtitle_language, canDelete = profile.canDelete && state.profiles.size > 1)
}

internal fun profilePageActions(controller: ApplicationController, values: ProfilePageValues?,
    parentalControls: () -> Unit, language: () -> Unit) = ProfilePageActions(
    rename = controller::saveProfileName,
    avatar = { pick, pin -> controller.setSelectedProfileAvatar(pick.style, pick.choice, pin) },
    kids = { enabled, pin -> controller.saveProfileKids(enabled, values?.maxAge ?: 12, pin) },
    maturity = { age, pin -> controller.saveProfileKids(true, age, pin) },
    approvedTitles = { values?.profile?.id?.let(controller::openParentTitles) },
    lock = controller::setSelectedProfilePin,
    parentalControls = parentalControls,
    language = language,
    delete = controller::deleteSelectedProfile,
)