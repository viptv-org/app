package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import java.io.File
import java.awt.EventQueue
import kotlinx.coroutines.*
import kotlin.math.abs
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PhoneAddonSettingsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
    private var frameTime = 0L
    private val fixtures = listOf(
        IntegrationConnection("addon:a", "stremio", "fixture", null, "Fixture A", true, 1, listOf(Capability.METADATA, Capability.SOURCES)),
        IntegrationConnection("addon:b", "stremio", "fixture", null, "Fixture B", true, 1, listOf(Capability.SOURCES)),
        IntegrationConnection("addon:c", "stremio", "fixture", null, "Fixture C", true, 1, listOf(Capability.LIVETV)))

    @Test fun emptyPhoneKeepsPrimaryAddReachableUnderACentredHeading() = onEdt {
        val scene = scene {
            AddonSettings(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, back = {})
        }
        try {
            frames(scene)
            val add = visibleAction(scene, "Add an add-on")
            assertTrue(add.boundsInRoot.bottom <= 844f, "Primary Add must be reachable before the installed list")
            val heading = nodes(scene).single { text(it) == "Add-ons" }
            assertTrue(abs(heading.boundsInRoot.center.x - 195f) < 1f, "Compact heading must remain centered")
            capture(scene, "phone-addons-empty")
        } finally { scene.close() }
    }

    @Test fun populatedPhonePreservesProfileSwitchesAndRemoval() = onEdt {
        var addons by mutableStateOf(fixtures)
        var enabled by mutableStateOf(setOf("addon:a", "addon:b"))
        val removed = mutableListOf<String>(); val toggled = mutableListOf<Pair<String, Boolean>>()
        val scene = scene {
            AddonSettings(true, addons, enabled, false, null, null, {}, { id, value ->
                toggled += id to value; enabled = if (value) enabled + id else enabled - id
            }, { id -> removed += id; addons = addons.filterNot { it.id == id } }, back = {})
        }
        try {
            frames(scene)
            val add = visibleAction(scene, "Add an add-on")
            assertTrue(add.boundsInRoot.bottom <= 844f)
            capture(scene, "phone-addons-populated")
            val control = descriptionAction(scene, "Enable Fixture C for this profile")
            assertEquals(ToggleableState.Off, control.config[SemanticsProperties.ToggleableState])
            invoke(control); frames(scene)
            assertEquals(listOf("addon:c" to true), toggled)
            assertEquals(ToggleableState.On, descriptionAction(scene, "Enable Fixture C for this profile").config[SemanticsProperties.ToggleableState])
            invoke(actions(scene, "Remove")[1]); frames(scene, 32)
            assertTrue(nodes(scene).any { text(it) == "Remove Fixture B?" })
            click(scene, "Cancel"); frames(scene, 32); assertTrue(removed.isEmpty())
            invoke(actions(scene, "Remove")[1]); frames(scene, 32)
            invoke(actions(scene, "Remove").last()); frames(scene, 32)
            assertEquals(listOf("addon:b"), removed)
            assertFalse(nodes(scene).any { text(it) == "Fixture B" })
        } finally { scene.close() }
    }

    @Test fun phoneInstallFailureRetainsDraftAndBusyControlsCannotSubmitAgainOrClose() = onEdt {
        var busy by mutableStateOf(false); var error by mutableStateOf<String?>(null)
        var backs = 0; val requests = mutableListOf<String>()
        val scene = scene {
            AddonSettings(true, emptyList(), emptySet(), busy, error, null,
                { requests += it; busy = true; error = null }, { _, _ -> }, {}, back = { backs++ })
        }
        try {
            frames(scene)
            click(scene, "Add an add-on"); frames(scene, 45)
            assertTrue(visibleAction(scene, "Install").config.contains(SemanticsProperties.Disabled))
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("  https://fixture.invalid/manifest.json  ")))
            frames(scene); capture(scene, "phone-add-addon-sheet")
            assertFalse(nodes(scene).any { text(it).contains("Valid add-on") })
            click(scene, "Install"); frames(scene)
            assertEquals(listOf("https://fixture.invalid/manifest.json"), requests)
            assertTrue(visibleAction(scene, "Install").config.contains(SemanticsProperties.Disabled))
            assertTrue(descriptionAction(scene, "Close add-on form").config.contains(SemanticsProperties.Disabled))
            busy = false; error = "The add-on could not be installed. Check the manifest and secure storage."; frames(scene)
            assertTrue(nodes(scene).any { text(it) == error })
            assertTrue(nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                .config.getOrNull(SemanticsProperties.EditableText)?.text?.contains("fixture.invalid") == true)
            capture(scene, "phone-add-addon-retry")
            click(scene, "Install"); frames(scene); assertEquals(2, requests.size)
            busy = false; error = "The add-on could not be installed. Check the manifest and secure storage."; frames(scene)
            invoke(descriptionAction(scene, "Close add-on form")); frames(scene, 45)
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) }); assertEquals(0, backs)
            invoke(descriptionAction(scene, "Back to settings")); assertEquals(1, backs)
        } finally { scene.close() }
    }

    @Test fun completedInstallClosesOnlyItsCurrentSheetAndImmediateRepeatCannotSubmitTwice() = onEdt {
        var error by mutableStateOf<String?>(null)
        var shown by mutableStateOf(true)
        val requests = mutableListOf<String>()
        val completions = mutableListOf<(Boolean) -> Unit>()
        val scene = scene {
            if (shown) AddonSettings(true, emptyList(), emptySet(), false, error, null,
                { fail("Completion contract should be used") }, { _, _ -> }, {},
                installWithCompletion = { url, done -> requests += url; completions += done; error = null })
        }
        try {
            frames(scene); click(scene, "Add an add-on"); frames(scene, 45)
            nodes(scene).single { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText]
                .action!!.invoke(AnnotatedString("  https://fixture.invalid/manifest.json  "))
            frames(scene)
            val submit = visibleAction(scene, "Install").config[SemanticsActions.OnClick].action!!
            val staleClose = descriptionAction(scene, "Close add-on form").config[SemanticsActions.OnClick].action!!
            submit.invoke(); submit.invoke(); staleClose.invoke()
            assertEquals(listOf("https://fixture.invalid/manifest.json"), requests)
            frames(scene)
            assertTrue(descriptionAction(scene, "Close add-on form").config.contains(SemanticsProperties.Disabled))
            completions[0](false); error = "The add-on could not be installed."; frames(scene)
            assertTrue(nodes(scene).any { text(it) == error })
            assertEquals("  https://fixture.invalid/manifest.json  ", nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
                .config[SemanticsProperties.EditableText].text, "Failure preserves the exact draft")
            click(scene, "Install"); frames(scene); assertEquals(2, requests.size)
            completions[0](true); frames(scene)
            assertTrue(descriptionAction(scene, "Close add-on form").config.contains(SemanticsProperties.Disabled),
                "Retired completion cannot close the retry")
            completions[1](true); frames(scene, 45)
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) })
            capture(scene, "phone-add-addon-success")
            click(scene, "Add an add-on"); frames(scene, 45)
            val reopenedField = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertEquals("", reopenedField.config[SemanticsProperties.EditableText].text, "Committed success clears the completed URL")
            assertTrue(visibleAction(scene, "Install").config.contains(SemanticsProperties.Disabled))
            capture(scene, "phone-add-addon-success-reopened-blank")
            reopenedField.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("https://second-fixture.invalid/manifest.json"))
            frames(scene)
            click(scene, "Install"); frames(scene)
            shown = false; frames(scene)
            shown = true; frames(scene)
            click(scene, "Add an add-on"); frames(scene, 45)
            completions[2](true); frames(scene)
            assertTrue(nodes(scene).any { it.config.contains(SemanticsActions.SetText) }, "Disposed request cannot close a new dialog")
        } finally { scene.close() }
    }

    @Test fun unavailableAddonManagementHidesEveryWriteAction() = onEdt {
        val scene = scene {
            AddonSettings(false, fixtures.take(1), emptySet(), false, null, null, {}, { _, _ -> }, {}, back = {},
                managementAvailable = false)
        }
        try {
            frames(scene)
            assertTrue(nodes(scene).any { text(it) == "Add-on management is not available on this connection." })
            assertTrue(actions(scene, "Add an add-on").isEmpty()); assertTrue(actions(scene, "Remove").isEmpty())
            assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Enable ") } == true })
        } finally { scene.close() }
    }

    @Test fun busyControllerRejectsStalePhonePropsWithoutLeavingTheNewSheetLatched() = onEdt {
        val fixture = LocalSetupFixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var commits = 0
        val local = object : LocalConnection by fixture.local {
            override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) { commits++ }
            override suspend fun sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot {
                entered.complete(Unit); release.await(); return SourceIntegrationSnapshot()
            }
        }
        val controller = ApplicationController(this, fixture.store); controller.local = local
        controller.mutable.value = ApplicationState(screen = Screen.Settings, localMode = true, selectedProfile = "default")
        controller.refreshSourceIntegrations(); entered.await()
        val existingJob = controller.job
        val scene = scene {
            AddonSettings(true, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {},
                installWithCompletion = controller::installAddon)
        }
        try {
            frames(scene); click(scene, "Add an add-on"); frames(scene, 45)
            nodes(scene).single { it.config.contains(SemanticsActions.SetText) }.config[SemanticsActions.SetText]
                .action!!.invoke(AnnotatedString("https://fixture.invalid/manifest.json"))
            frames(scene); click(scene, "Install"); frames(scene)
            assertEquals(0, commits); assertSame(existingJob, controller.job); assertFalse(existingJob!!.isCancelled)
            assertFalse(visibleAction(scene, "Install").config.contains(SemanticsProperties.Disabled))
            invoke(descriptionAction(scene, "Close add-on form")); frames(scene, 45)
            assertFalse(nodes(scene).any { it.config.contains(SemanticsActions.SetText) })
            release.complete(Unit); existingJob.join()
        } finally { release.complete(Unit); scene.close(); fixture.close(controller) }
    }

    @Test fun wideLayoutKeepsItsInlineInstallInsteadOfACompactAddSheet() = onEdt {
        val requests = mutableListOf<String>()
        val scene = scene(width = 1440, height = 900) {
            AddonSettings(false, emptyList(), emptySet(), false, null, null, { requests += it }, { _, _ -> }, {},
                back = {})
        }
        try {
            frames(scene)
            assertTrue(actions(scene, "Add an add-on").isEmpty())
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("https://fixture.invalid/manifest.json")))
            frames(scene); click(scene, "Install")
            assertEquals(listOf("https://fixture.invalid/manifest.json"), requests)
        } finally { scene.close() }
    }

    private fun scene(width: Int = 390, height: Int = 844, content: @Composable () -> Unit) = ImageComposeScene(width, height, Density(1f)) {
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background, content = content) }
    }
    private suspend fun frames(scene: ImageComposeScene, count: Int = 24) = repeat(count) { frameTime += 16_666_667L; check(EventQueue.isDispatchThread()); scene.render(frameTime).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun actions(scene: ImageComposeScene, label: String) = nodes(scene).filter { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun visible(node: SemanticsNode, height: Float = 844f) = node.boundsInRoot.height > 0f && node.boundsInRoot.top >= 0f && node.boundsInRoot.bottom <= height
    private fun visibleAction(scene: ImageComposeScene, label: String) = assertNotNull(actions(scene, label).firstOrNull { visible(it) }, "$label is outside the compact viewport")
    private fun descriptionAction(scene: ImageComposeScene, label: String) = nodes(scene).single {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true && it.config.contains(SemanticsActions.OnClick)
    }
    private fun invoke(node: SemanticsNode) { assertFalse(node.config.contains(SemanticsProperties.Disabled)); assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun click(scene: ImageComposeScene, label: String) = invoke(visibleAction(scene, label))
    private suspend fun scrollToAction(scene: ImageComposeScene, label: String) {
        repeat(8) {
            if (actions(scene, label).any { visible(it) }) return
            nodes(scene).first { it.config.contains(SemanticsActions.ScrollBy) }.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 260f)
            frames(scene)
        }
        assertNotNull(actions(scene, label).firstOrNull { visible(it) }, "$label was not reachable by scrolling")
    }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_PHONE_ADDONS_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); frameTime += 16_666_667L
        scene.render(frameTime).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
