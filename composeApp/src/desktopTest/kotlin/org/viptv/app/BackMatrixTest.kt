package org.viptv.app

import kotlinx.coroutines.*
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.state.*
import kotlin.test.*

class BackMatrixTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun <T> controller(start: ApplicationState, block: (ApplicationController) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = ApplicationController(scope, store)
            controller.mutable.value = start
            return block(controller)
        } finally { scope.cancel() }
    }

    private fun ApplicationController.androidBack(): Boolean {
        if (!state.value.systemBackStaysInApp()) return false
        navigateSystemBack(); return true
    }

    private fun signedIn(screen: Screen, navBack: List<Screen> = emptyList()) =
        ApplicationState(screen = screen, navBack = navBack, selectedProfile = "1", localMode = true)

    private val pages = listOf(Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime, Screen.Search,
        Screen.Library, Screen.History, Screen.Calendar, Screen.Live, Screen.Settings)

    @Test fun everyPageGoesBackToTheScreenItCameFromOnEveryLayout() {
        for (page in pages) for (input in listOf("Android/TV Back", "navigateBack")) {
            controller(signedIn(page, navBack = listOf(Screen.Home, Screen.Calendar))) { c ->
                assertTrue(c.state.value.canNavigateBack(), "$page offers no Back")
                if (input == "navigateBack") c.navigateBack() else assertTrue(c.androidBack(), "$input left the app from $page")
                assertEquals(Screen.Calendar, c.state.value.screen, "$input from $page")
                assertEquals(listOf(Screen.Home), c.state.value.navBack, "$input from $page")
            }
        }
    }

    @Test fun aPageWithoutHistoryGoesHomeWithoutRecordingItself() {
        for (page in pages) controller(signedIn(page)) { c ->
            assertTrue(c.androidBack(), "Android Back left the app from $page")
            assertEquals(Screen.Home, c.state.value.screen, "from $page")
            assertEquals(emptyList(), c.state.value.navBack, "from $page")
        }
    }

    @Test fun homeBacksToProfilesOnAndroidButDesktopAndBrowserPopTheirHistory() {
        controller(signedIn(Screen.Home)) { c ->
            assertTrue(c.state.value.systemBackStaysInApp(), "Android/TV Back on Home must open Profiles")
            assertTrue(c.androidBack())
            assertEquals(Screen.Profiles, c.state.value.screen)
        }
        controller(signedIn(Screen.Home, navBack = listOf(Screen.Movies))) { c ->
            assertTrue(c.state.value.canNavigateBack())
            c.navigateBack()
            assertEquals(Screen.Movies, c.state.value.screen)
        }
        controller(signedIn(Screen.Home)) { c ->
            assertFalse(c.state.value.canNavigateBack(), "Home without history has nothing to go back to")
            c.navigateBack()
            assertEquals(Screen.Home, c.state.value.screen)
        }
    }

    @Test fun profilesReachedFromHomeLeavesTheAppWithNoBounce() {
        controller(signedIn(Screen.Home)) { c ->
            assertTrue(c.androidBack(), "Android/TV Back on Home opens Profiles")
            assertEquals(Screen.Profiles, c.state.value.screen)
            assertFalse(c.state.value.systemBackStaysInApp(), "Back on the picker reached from Home leaves the app")
            assertFalse(c.state.value.canNavigateBack(), "no Home<->Profiles bounce")
            assertFalse(c.androidBack(), "the activity leaves, the app does not navigate")
            assertEquals(Screen.Profiles, c.state.value.screen)
        }
    }

    @Test fun profilesGoesBackToWhereTheViewerCameFromOnceAProfileIsChosen() {
        controller(signedIn(Screen.Profiles, navBack = listOf(Screen.Settings))) { c ->
            assertTrue(c.androidBack(), "Android Back left the app from the profile switcher")
            assertEquals(Screen.Settings, c.state.value.screen)
        }
        controller(signedIn(Screen.Profiles)) { c ->
            assertTrue(c.androidBack())
            assertEquals(Screen.Home, c.state.value.screen)
            assertEquals(emptyList(), c.state.value.navBack)
        }
        controller(ApplicationState(screen = Screen.Profiles, localMode = true)) { c ->
            assertFalse(c.state.value.systemBackStaysInApp())
            assertFalse(c.state.value.canNavigateBack())
            c.navigateBack()
            assertEquals(Screen.Profiles, c.state.value.screen)
        }
    }

    @Test fun manageProfilesClosesBeforeProfilesIsLeft() {
        for (selected in listOf(null, "1")) controller(ApplicationState(screen = Screen.Profiles, selectedProfile = selected,
            managingProfiles = true, navBack = listOf(Screen.Home), localMode = true)) { c ->
            assertTrue(c.androidBack(), "Android Back left the app from Manage profiles")
            assertEquals(Screen.Profiles, c.state.value.screen)
            assertFalse(c.state.value.managingProfiles)
        }
    }

    @Test fun profilePinAndEditorGoUpToProfilesWhateverWasOpenBefore() {
        for (screen in listOf(Screen.ProfilePin, Screen.ProfileEdit)) for (selected in listOf(null, "1")) {
            controller(ApplicationState(screen = screen, selectedProfile = selected, profileToOpen = "2", profileToEdit = "2",
                navBack = listOf(Screen.Home), localMode = true)) { c ->
                assertTrue(c.state.value.canNavigateBack())
                assertTrue(c.androidBack(), "Android Back left the app from $screen")
                assertEquals(Screen.Profiles, c.state.value.screen, "from $screen")
                assertEquals(listOf(Screen.Home), c.state.value.navBack, "from $screen")
            }
        }
    }

    @Test fun connectIsARootAndRecoveryCodesKeepTheViewerOnThePage() {
        controller(ApplicationState(screen = Screen.Connect)) { c ->
            assertFalse(c.state.value.systemBackStaysInApp())
            assertFalse(c.state.value.canNavigateBack())
        }
        controller(ApplicationState(screen = Screen.RecoveryCodes, recoveryKit = null)) { c ->
            assertTrue(c.androidBack())
            assertEquals(Screen.RecoveryCodes, c.state.value.screen)
            assertFalse(c.state.value.canNavigateBack())
        }
    }

    @Test fun overlaysAndSettingsLevelsStayAsTheyWere() {
        controller(signedIn(Screen.Sources).copy(sourceReturnScreen = Screen.Details)) { c ->
            assertTrue(c.androidBack()); assertEquals(Screen.Details, c.state.value.screen)
        }
        controller(signedIn(Screen.Details, navBack = listOf(Screen.Search)).copy(detailReturnScreen = Screen.Search)) { c ->
            assertTrue(c.androidBack()); assertEquals(Screen.Search, c.state.value.screen)
        }
        controller(signedIn(Screen.Settings, navBack = listOf(Screen.Home)).copy(settingsPage = SettingsPage("Add-ons", subpage = true))) { c ->
            assertTrue(c.state.value.canNavigateBack())
            assertTrue(c.androidBack())
            assertEquals(Screen.Settings, c.state.value.screen); assertEquals(SettingsPage(), c.state.value.settingsPage)
        }
    }
}
