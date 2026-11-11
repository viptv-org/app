package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.state.navigateBack
import org.viptv.app.state.navigate
import org.viptv.app.state.configureSource
import kotlin.test.*

class SourceConfigureNavigationTest {
    @Test fun configureOpensTheSettingsPageThatOwnsTheProducer(): Unit = runBlocking {
        val fixture = LocalSetupFixture()
        val controller = fixture.controller()
        controller.local = fixture.local
        controller.mutable.value = ApplicationState(screen = Screen.Home, localMode = true, selectedProfile = "default")
        try {
            controller.mutable.value = ApplicationState(screen = Screen.Sources, localMode = true, selectedProfile = "default")
            controller.configureSource("addon:7")
            assertEquals(Screen.Settings, controller.mutable.value.screen)
            assertEquals("Add-ons", controller.mutable.value.settingsEntrySection)
            assertEquals("addon:7", controller.mutable.value.settingsAddonTarget, "the Add-ons page knows which row to open on")
            controller.navigateBack()
            assertEquals(Screen.Home, controller.mutable.value.screen, "Back leaves Settings for the screen behind the picker")
            assertNull(controller.mutable.value.settingsAddonTarget, "leaving Settings drops the target")

            controller.configureSource("vsources:2")
            assertEquals("Source integrations", controller.mutable.value.settingsEntrySection)
            assertNull(controller.mutable.value.settingsAddonTarget, "only an add-on names a row on the Add-ons page")
            controller.navigate(Screen.Home)

            controller.configureSource("m3u:3")
            assertEquals("Source integrations", controller.mutable.value.settingsEntrySection)
            controller.navigate(Screen.Home)

            controller.configureSource("iptv:4")
            assertEquals("Xtream connections", controller.mutable.value.settingsEntrySection)
            controller.navigate(Screen.Home)

            controller.configureSource("simkl")
            assertEquals(Screen.Home, controller.mutable.value.screen, "a producer with no Settings page opens nothing")
        } finally { fixture.close(controller) }
    }
}