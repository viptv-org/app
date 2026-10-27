package org.viptv.app

import org.junit.Rule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.features.settings.SettingsInformation
import org.viptv.app.features.settings.TvAboutSettings
import org.viptv.app.theme.ViptvTheme
import org.viptv.app.updates.*
import kotlin.test.*

/** app#64: "Update ready" outside the player, and the update rows in Settings › About. */
@OptIn(ExperimentalComposeUiApi::class)
class AppUpdateUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val platform = FakeUpdatePlatform(feedJson(1301))
    private val updater = AppUpdater(platform, scope) { "https://viptv.example/updates/android/update.json" }

    @AfterTest fun close() { scope.cancel() }

    private class Scene(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit) : AutoCloseable {
        val scene = ImageComposeScene(width, height, Density(1f), content = content)
        private var frame = 0L
        fun render() = repeat(12) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        fun shows(value: String) = nodes().any { value in text(it) }
        /** A Text inside a clickable merges into its parent; click the clickable ancestor of [value]. */
        fun clickContaining(value: String) {
            val node = nodes().first { it.config.contains(SemanticsActions.OnClick) && (value in text(it) ||
                it.children.any { child -> value in text(child) }) }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); render()
        }
        override fun close() = scene.close()
    }

    @Test fun phonesShowANonBlockingBannerOutsideThePlayerUntilLater() {
        updater.foreground(playing = false)
        assertIs<UpdateStatus.Ready>(updater.state.value.status)
        var allowed by mutableStateOf(false)
        Scene(390, 844) { ViptvTheme { AppUpdatePrompt(updater, television = false, allowed = allowed) } }.use { scene ->
            scene.render()
            assertFalse(scene.shows("Update ready"), "Never over the player")
            allowed = true; scene.render()
            assertTrue(scene.shows("Update ready"))
            assertTrue(scene.shows("VIPTV 0.1.1301 is ready to install."))
            scene.clickContaining("Later")
            assertFalse(scene.shows("Update ready"))
            assertEquals(setOf(1301L), updater.state.value.snoozed)
        }
    }

    @Test fun installWithoutThePermissionExplainsAndOpensTheSettingsScreen() {
        platform.installAllowed = false
        updater.foreground(playing = false)
        Scene(390, 844) { ViptvTheme { AppUpdatePrompt(updater, television = false, allowed = true) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Allow updates"))
            scene.clickContaining("Open settings")
            assertEquals(1, platform.permissionOpened)
        }
    }

    @Test fun theTvPromptIsADpadDialogWithInstallAndLater() {
        updater.foreground(playing = false)
        Scene(1920, 1080) { ViptvTheme(television = true) { AppUpdatePrompt(updater, television = true, allowed = true) } }.use { scene ->
            scene.render()
            assertTrue(scene.shows("Update ready"))
            scene.clickContaining("Install")
            assertEquals(listOf(1301L), platform.installs.map { it.versionCode })
        }
    }

    @Test fun phoneAboutShowsTheVersionTheStateAndChecksOnDemand() {
        platform.feed = feedJson(1300)
        Scene(390, 1200) {
            ViptvTheme { CompositionLocalProvider(LocalAppUpdater provides updater) {
                SettingsInformation("About", local = false, address = "https://viptv.example/", profileName = null, profiles = {}, changeConnection = {})
            } }
        }.use { scene ->
            scene.render()
            assertTrue(scene.shows("Version 0.1.300+aaaaaaa"))
            assertTrue(scene.shows("Not checked yet"))
            scene.clickContaining("Check for updates")
            assertEquals(1, platform.fetched.size)
            assertTrue(scene.shows("Up to date"))
            assertTrue(scene.shows("Checked just now"))
        }
    }

    @Test fun tvAboutHasOneUpdateRowThatTurnsIntoInstall() {
        Scene(1920, 1080) {
            ViptvTheme(television = true) { CompositionLocalProvider(LocalAppUpdater provides updater) {
                TvAboutSettings(local = false, address = "https://viptv.example/", profileName = null, addonCount = 0, enabledCount = 0,
                    error = null, notice = null, scale = 1f, initial = FocusRequester(), sidebar = FocusRequester())
            } }
        }.use { scene ->
            scene.render()
            assertTrue(scene.shows("0.1.300+aaaaaaa"))
            scene.clickContaining("Check for updates")
            assertTrue(scene.shows("VIPTV 0.1.1301 is ready to install"))
            scene.clickContaining("Install update")
            assertEquals(1, platform.installs.size)
        }
    }
}
