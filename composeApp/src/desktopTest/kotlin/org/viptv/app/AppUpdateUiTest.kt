package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import org.viptv.app.features.settings.SettingsInformation
import org.viptv.app.features.settings.TvAboutSettings
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.app.updates.*
import org.junit.Rule
import java.io.File
import kotlin.test.*

/** app#64/app#139: the update prompt, the full-screen Updating screen and the update rows in Settings › About. */
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
        /** docs/evidence renders, only when a capture directory is given. */
        fun capture(name: String) {
            val dir = System.getenv("VIPTV_UPDATE_CAPTURE_DIR") ?: return
            File(dir).mkdirs()
            scene.render(frame++ * 16_666_667L).use { image -> image.encodeToData()!!.use { File(dir, "$name.png").writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }

    /** The page behind the update surfaces: an opaque stand-in for Home. */
    @androidx.compose.runtime.Composable
    private fun Page() = Box(Modifier.fillMaxSize().background(Palette.background))

    @androidx.compose.runtime.Composable
    private fun Updates(television: Boolean, allowed: Boolean = true) =
        AppWithUpdates(updater, television, promptAllowed = allowed) { Page() }

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

    @Test fun installWithoutThePermissionKeepsTheUpdatingScreenAndOpensTheSettingsScreen() {
        platform.installAllowed = false
        updater.foreground(playing = false)
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Allow updates"), "The Updating screen explains the permission step")
            assertTrue(scene.shows("Allow VIPTV to install apps in the screen that opens, then come back to finish the update."))
            assertFalse(scene.shows("Update ready"), "The prompt is gone once Install was chosen")
            scene.clickContaining("Open settings")
            assertEquals(1, platform.permissionOpened)
            scene.capture("phone-updating-permission")
        }
    }

    @Test fun onATvTheUpdatingScreenCoversThePageWithProgressAndTheRestartLine() {
        updater.foreground(playing = false)
        platform.installGate = CompletableDeferred()
        Scene(1920, 1080) { ViptvTheme(television = true) { Updates(television = true) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Updating VIPTV to 0.1.1301…"))
            assertTrue(scene.shows("Writing the update. Android asks you to confirm before it installs."))
            assertEquals(1, scene.nodes().count { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null },
                "A progress indicator is on screen")
            assertTrue(scene.shows("VIPTV restarts by itself when the update is installed."))
            assertFalse(scene.shows("Update ready"))
            scene.capture("tv-updating")
        }
    }

    @Test fun phonesGetTheSameFullScreenWithProgress() {
        updater.foreground(playing = false)
        platform.installGate = CompletableDeferred()
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Updating VIPTV to 0.1.1301…"))
            assertTrue(scene.shows("Writing the update. Android asks you to confirm before it installs."))
            scene.capture("phone-updating")
        }
    }

    @Test fun theUpdatingScreenWaitsBehindAndroidsConfirmation() {
        updater.foreground(playing = false)
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Confirm the update in the dialog Android is showing."))
            scene.capture("phone-updating-confirm")
        }
    }

    @Test fun aFailedInstallLeavesTheUpdatingScreenAndOffersTryAgain() {
        updater.foreground(playing = false)
        platform.installGate = CompletableDeferred()
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            assertTrue(scene.shows("Updating VIPTV to 0.1.1301…"))
            platform.installGate!!.complete(Unit); scene.render()
            platform.installResult!!(InstallResult.Failed); scene.render()
            assertFalse(scene.shows("Updating VIPTV to 0.1.1301…"), "Never stuck on the Updating screen")
            assertTrue(scene.shows("Couldn't update VIPTV"))
            assertTrue(scene.shows("Android didn't install the update."))
            scene.clickContaining("Try again")
            assertEquals(2, platform.installs.size)
        }
    }

    @Test fun aCancelledInstallLeavesTheUpdatingScreen() {
        updater.foreground(playing = false)
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            scene.clickContaining("Install")
            platform.installResult!!(InstallResult.Cancelled); scene.render()
            assertFalse(scene.shows("Updating VIPTV to 0.1.1301…"))
            assertFalse(scene.shows("Update ready"), "Backing out of Android's confirmation acts as Later")
        }
    }

    @Test fun theNewVersionIsConfirmedBrieflyAfterTheRelaunch() {
        updater.announceUpdated("0.1.301+bbbbbbb")
        Scene(390, 844) { ViptvTheme { Updates(television = false) } }.use { scene ->
            scene.render()
            assertTrue(scene.shows("Updated to 0.1.301+bbbbbbb"))
            scene.capture("phone-updated")
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