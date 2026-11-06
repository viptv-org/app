package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
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
            controller.configureSource("addon:7")
            assertEquals(Screen.Settings, controller.mutable.value.screen)
            assertEquals("Add-ons", controller.mutable.value.settingsEntrySection)
            controller.navigate(Screen.Home)

            controller.configureSource("vsources:2")
            assertEquals("Source integrations", controller.mutable.value.settingsEntrySection)
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