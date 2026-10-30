package org.viptv.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.features.settings.SettingsRoute
import org.viptv.app.features.settings.stremioImportSection
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

private object NoSavedSession : RemoteSessionStore {
    override suspend fun read(): SavedRemoteSession? = null
    override suspend fun save(session: SavedRemoteSession) = Unit
    override suspend fun clear() = Unit
}

@OptIn(ExperimentalComposeUiApi::class)
class SettingsBackTest {
    @get:Rule val uiThread = UiThreadRule()
    private class Harness(browser: Boolean, start: ApplicationState, height: Int = 844) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession).also { it.mutable.value = start }
        var systemBack: (() -> Unit)? = null
        val scene = ImageComposeScene(390, height, Density(1f)) {
            val state by controller.state.collectAsState()
            ViptvTheme { if (state.screen == Screen.Settings) SettingsRoute(state, controller, browser, television = false, compact = true,
                entryBackChanged = { systemBack = it }) }
        }
        private var frame = 0L
        fun render() = repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun labelled(node: SemanticsNode, value: String) =
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true ||
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        fun shows(value: String) = nodes().any { labelled(it, value) }
        fun click(value: String) {
            val node = nodes().firstOrNull { labelled(it, value) && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$value' in ${nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { text -> text.text } }}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); render()
        }
        fun atSettingsList() = controller.state.value.screen == Screen.Settings && shows("Settings") && shows("Switch profile")
        override fun close() { scene.close(); scope.cancel() }
    }

    private fun settings(navBack: List<Screen> = listOf(Screen.Home), localMode: Boolean = true) =
        ApplicationState(screen = Screen.Settings, navBack = navBack, selectedProfile = "1", localMode = localMode)

    @Test fun backFromEachPhoneSubPageReturnsToTheSettingsList() = Harness(browser = false, settings()).use { ui ->
        assertBackFromEachRow(ui, listOf("Add-ons", "Playback", "Subtitles & audio", "Account", "Parental controls", "Help", "About", "Watch on TV"))
    }

    @Test fun backFromEachServerSubPageOfAPhoneWidthBrowserReturnsToTheSettingsList() =
        Harness(browser = true, settings(localMode = false), height = 1400).use { ui ->
            assertBackFromEachRow(ui, listOf("Devices & sessions", stremioImportSection, "Watch on TV", "Add-ons", "Account", "Parental controls"))
        }

    private fun assertBackFromEachRow(ui: Harness, rows: List<String>) {
        ui.render()
        assertTrue(ui.atSettingsList())
        for (row in rows) {
            ui.click(row)
            assertFalse(ui.atSettingsList(), "$row did not open a sub-page")
            ui.controller.navigateBack(); ui.render()
            assertTrue(ui.atSettingsList(), "Back from $row left Settings for ${ui.controller.state.value.screen}")
        }
    }

    @Test fun backFromNestedParentalTitlesGoesUpOneLevelAtATime() = Harness(browser = true, settings(localMode = false)).use { ui ->
        ui.render()
        ui.click("Parental controls")
        ui.controller.mutable.value = ui.controller.state.value.copy(parentTitles = ParentTitlesState("2", "Kid"))
        ui.render()
        assertTrue(ui.shows("Approved titles"))
        ui.controller.navigateBack(); ui.render()
        assertNull(ui.controller.state.value.parentTitles)
        assertEquals(Screen.Settings, ui.controller.state.value.screen)
        assertFalse(ui.shows("Approved titles"))
        assertTrue(ui.shows("Parental controls") && !ui.atSettingsList(), "Back from Titles skipped Parental controls")
        ui.controller.navigateBack(); ui.render()
        assertTrue(ui.atSettingsList())
    }

    @Test fun backFromTheSettingsListIsUnchanged() {
        Harness(browser = false, settings(navBack = listOf(Screen.Library))).use { ui ->
            ui.render()
            ui.controller.navigateBack(); ui.render()
            assertEquals(Screen.Library, ui.controller.state.value.screen)
        }
        Harness(browser = false, settings(navBack = emptyList())).use { ui ->
            ui.render()
            assertNull(ui.systemBack, "The Settings list must leave Android Back to the activity")
            ui.controller.navigateBack(); ui.render()
            assertEquals(Screen.Home, ui.controller.state.value.screen)
        }
    }

    @Test fun androidSystemBackAndTheInPageArrowTakeTheSameStepAsNavigateBack() = Harness(browser = false, settings()).use { ui ->
        ui.render()
        for (route in listOf("in-page arrow", "Android Back", "navigateBack")) {
            ui.click("Add-ons")
            assertEquals(SettingsPage("Add-ons", subpage = true), ui.controller.state.value.settingsPage)
            val systemBack = assertNotNull(ui.systemBack, "Android Back is not routed into the open sub-page")
            when (route) {
                "in-page arrow" -> ui.click("Back to settings")
                "Android Back" -> { systemBack(); ui.render() }
                else -> { ui.controller.navigateBack(); ui.render() }
            }
            assertTrue(ui.atSettingsList(), "$route did not return to the Settings list")
            assertEquals(SettingsPage(), ui.controller.state.value.settingsPage, route)
            assertNull(ui.systemBack, "$route left Android Back routed into Settings")
        }
    }

    @Test fun wideSectionsAreNotSubPagesAndAPageLastsOneVisit() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ApplicationController(scope, NoSavedSession)
        controller.mutable.value = settings(navBack = listOf(Screen.Search)).copy(settingsPage = SettingsPage("Devices"))
        controller.navigateBack()
        assertEquals(Screen.Search, controller.state.value.screen)
        assertNull(controller.state.value.settingsPage, "Leaving Settings must forget its page")
        controller.mutable.value = controller.state.value.copy(screen = Screen.Search, settingsPage = SettingsPage("Add-ons", subpage = true))
        controller.navigate(Screen.Settings)
        assertNull(controller.state.value.settingsPage, "Settings must open at its entry section, not a stale sub-page")
        scope.cancel()
    }

    @Test fun backReturnsToTheListScrolledWhereTheRowWasOpened() = Harness(browser = false, settings(), height = 480).use { ui ->
        ui.render()
        fun list() = ui.nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
        fun offset() = list().config[SemanticsProperties.VerticalScrollAxisRange].value()
        list().config[SemanticsActions.ScrollBy].action!!.invoke(0f, 2000f); ui.render()
        val scrolled = offset()
        assertTrue(scrolled > 0f, "The Settings list did not scroll at this height")
        ui.click("Add-ons")
        ui.controller.navigateBack(); ui.render()
        assertEquals(scrolled, offset())
        assertTrue(ui.shows("Add-ons"))
    }
}
