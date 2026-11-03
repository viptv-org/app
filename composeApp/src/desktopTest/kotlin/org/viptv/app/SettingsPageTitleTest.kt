package org.viptv.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.LocalBarsFadeContent
import org.viptv.app.features.settings.AccountDevicesScreen
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.XtreamConnectionsSettings
import org.viptv.app.theme.ViptvTheme
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SettingsPageTitleTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun frames(scene: ImageComposeScene, count: Int = 16) =
        repeat(count) { scene.render(it * 16_666_667L).close(); Thread.sleep(4) }

    // The fade and the header both come alive only against the page background, so name the provider explicitly.
    private fun scene(content: @Composable () -> Unit) = ImageComposeScene(390, 844, Density(1f)) {
        CompositionLocalProvider(LocalBarsFadeContent provides true) {
            ViptvTheme { content() }
        }
    }

    private fun title(scene: ImageComposeScene, text: String): SemanticsNode =
        nodes(scene).first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true
        }

    private val pages: List<Pair<String, @Composable () -> Unit>> = listOf(
        "Settings" to { PhoneSettings(null, 0, true, null, {}, {}, {}, back = {}) },
        "Add-ons" to { AddonSettings(true, emptyList(), emptySet(), false, null, null, { _ -> }, { _, _ -> }, { _ -> }, back = {}) },
        "Xtream connections" to { XtreamConnectionsSettings(null, null, false, null, null, false, null,
            compact = true, back = {}, request = {}, create = { _, _, _, _, _ -> }, update = { _, _, _ -> },
            replacePassword = { _, _ -> }, sync = { _ -> }, setDefault = { _ -> }, remove = { _ -> }) },
        "Devices" to { AccountDevicesScreen(null, false, null, {}, {}, {}, compact = true, back = {}) },
        "Profile" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Profile", profilePage = { _ -> Text("content") }) },
        "Linked services" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Linked services", linkedServices = { _ -> Text("content") }) },
        "Parental controls" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Parental controls", parental = { Text("content") }) },
        "Playback" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Playback", sectionPreferences = { _, _ -> Text("content") }) },
        "Subtitles & audio" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Subtitles & audio", sectionPreferences = { _, _ -> Text("content") }) },
        "Account" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Account", account = { Text("content") }) },
        "Change password" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Password", password = { _ -> Text("content") }) },
        "Household PIN" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Household PIN", householdPin = { Text("content") }) },
        "Delete account" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Delete account", deleteAccount = { _ -> Text("content") }) },
        "Source integrations" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Source integrations", sourceIntegrations = { _, _, _ -> Text("content") }) },
        "VOD matches" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "VOD matches", vodMatches = { _ -> Text("content") }) },
        "Playback gateways" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Gateways", gateways = { _, _, _ -> Text("content") }) },
        "Watch on TV" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Watch on TV", watchOnTvSettings = { _, _ -> Text("content") }) },
        "Help" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "Help") },
        "About VIPTV" to { SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
            initialSection = "About") },
    )

    @Test fun everySettingsPageTitleSitsInTheSameHeaderSlot() {
        var expected: Rect? = null
        for ((name, content) in pages) {
            val scene = scene(content)
            try {
                frames(scene)
                val node = title(scene, name)
                val rect = node.boundsInRoot
                assertTrue(rect.height < 30f, "$name title is not the shared 17sp header: height ${rect.height}")
                assertTrue(abs(rect.left + rect.width / 2 - 195f) <= 1f,
                    "$name title is not centred: ${rect.left}..${rect.right}")
                assertTrue(rect.top < 56f, "$name title is not in the header row: top ${rect.top}")
                val slot = if (expected == null) { expected = rect; rect } else expected
                assertTrue(abs(slot.top - rect.top) <= 1f && abs(slot.bottom - rect.bottom) <= 1f &&
                    abs(slot.left - rect.left) <= 1f && abs(slot.right - rect.right) <= 1f,
                    "$name title is in a different slot: $rect vs $slot")
            } finally { scene.close() }
        }
    }
}