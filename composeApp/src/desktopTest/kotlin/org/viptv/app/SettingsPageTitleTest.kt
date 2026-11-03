package org.viptv.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.LocalBarsFadeContent
import org.viptv.app.features.settings.AccountDevicesScreen
import org.viptv.app.features.settings.AddonSettings
import org.viptv.app.features.settings.PhoneSettings
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.XtreamConnectionsSettings
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SettingsPageTitleTest {
    @get:Rule val uiThread = UiThreadRule()

    private var frame = 0L
    private val background = Palette.background.toArgb()

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun frames(scene: ImageComposeScene, count: Int = 16) =
        repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }

    // The fade and the header both read their colour from the page background, so name the provider explicitly.
    private fun scene(width: Int = 390, height: Int = 844, content: @Composable () -> Unit) =
        ImageComposeScene(width, height, Density(1f)) {
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
        "Xtream connections" to { XtreamConnectionsSettings(null, null, false, null, null, true, null,
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
            val scene = scene(content = content)
            try {
                frames(scene)
                val node = title(scene, name)
                val rect = node.boundsInRoot
                assertTrue(rect.height < 30f, "$name title is not the shared 17sp header: height ${rect.height}")
                assertTrue(abs(rect.left + rect.width / 2 - 195f) <= 1f,
                    "$name title is not centred: ${rect.left}..${rect.right}")
                assertTrue(rect.top < 56f, "$name title is not in the header row: top ${rect.top}")
                val slot = if (expected == null) { expected = rect; rect } else expected
                assertTrue(abs(slot.top - rect.top) <= 1f && abs(slot.bottom - rect.bottom) <= 1f,
                    "$name title sits at a different height: $rect vs $slot")
            } finally { scene.close() }
        }
    }

    @Test fun scrolledSettingsListFadesRowsUnderThePinnedHeader() {
        val scene = scene(390, 480) {
            PhoneSettings(null, 0, true, null, profiles = {}, addons = {}, leave = {}, back = {},
                account = {}, devices = {}, stremioImport = {}, preferences = { _ -> }, parental = {},
                information = { _ -> }, addDevice = {}, householdPin = {}, sourceIntegrations = {},
                xtreamConnections = {}, password = {}, deleteAccount = {}, profilePage = {},
                linkedServices = {}, gateways = {}, vodMatches = {}, watchOnTv = true, openWatchOnTv = {})
        }
        try {
            frames(scene)
            val list = nodes(scene).first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
            list.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 4000f)
            frames(scene)
            val column = readColumn(scene, 20, 57, 26)
            assertNear(background, column.first(), "a row fades into the background under the pinned header")
            assertTrue(distance(column.last(), background) > 8, "a row keeps its colour below the fade")
        } finally { scene.close() }
    }

    private fun readColumn(scene: ImageComposeScene, x: Int, fromY: Int, rows: Int): List<Int> =
        scene.render(frame++ * 16_666_667L).use { image ->
            Bitmap.makeFromImage(image).use { pixels -> (0 until rows).map { row -> pixels.getColor(x, fromY + row) } }
        }

    private fun distance(a: Int, b: Int) = (0..2).maxOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }
    private fun assertNear(expected: Int, actual: Int, message: String) =
        assertTrue(distance(expected, actual) <= 3, "$message: expected ${hex(expected)}, was ${hex(actual)}")
    private fun hex(color: Int) = "#" + color.toUInt().toString(16).padStart(8, '0')
}