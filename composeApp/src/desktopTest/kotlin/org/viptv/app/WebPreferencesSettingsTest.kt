package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.viptv.app.components.TopNavigation
import org.viptv.app.connection.Profile
import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.features.settings.ProfilePreferencesSettings
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WebPreferencesSettingsTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun subtitlesAndAudioRenderSelectorsAndPreviewAtDesktopWidth() {
        Ui(1920, 1080) {
            SettingsScreen(local = false, addons = emptyList(), enabledAddonIds = emptySet(), busy = false, error = null,
                sessionNotice = null, install = {}, toggle = { _, _ -> }, remove = {}, profiles = {}, leave = {},
                initialSection = "Subtitles & audio", browser = true,
                sectionPreferences = { section, _ ->
                    ProfilePreferencesSettings(section, PreferencesSnapshot(9, ProfilePreferences()), false, null, {}, {})
                })
        }.use { ui ->
            val triggers = ui.triggers()
            assertEquals(listOf("Text size: Medium", "Background: None", "Text colour: White",
                "Audio language: Original audio", "Subtitles: Always", "Subtitle language: None"), triggers)
            assertTrue(ui.nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Subtitle style preview") == true },
                "the live subtitle preview renders over the still frame")
            assertTrue(ui.nodes().any { ui.text(it) == "I didn’t hear you come in." })
        }
    }

    @Test fun playbackRendersTogglesAndSourceSelectorsAtDesktopWidth() {
        Ui(1920, 1080) {
            SettingsScreen(local = false, addons = emptyList(), enabledAddonIds = emptySet(), busy = false, error = null,
                sessionNotice = null, install = {}, toggle = { _, _ -> }, remove = {}, profiles = {}, leave = {},
                initialSection = "Playback", browser = true,
                sectionPreferences = { section, _ ->
                    ProfilePreferencesSettings(section, PreferencesSnapshot(9, ProfilePreferences()), false, null, {}, {})
                })
        }.use { ui ->
            val triggers = ui.triggers()
            assertEquals(listOf("Default source: Always ask", "Preferred quality: Auto", "HDR and Dolby Vision: No preference"), triggers)
            val nextEpisode = ui.switch("Next episode button")
            assertEquals(ToggleableState.On, nextEpisode.config[SemanticsProperties.ToggleableState])
            val skipIntros = ui.switch("Skip intros and recaps")
            assertEquals(ToggleableState.Off, skipIntros.config[SemanticsProperties.ToggleableState])
        }
    }

    @Test fun pickingLanguagesSavesTheSameTagsThePhoneSaves() {
        val saved = mutableListOf<ProfilePreferences>()
        Ui(800, 1000) {
            Box(Modifier.widthIn(max = 760.dp)) {
                ProfilePreferencesSettings("Subtitles & audio", PreferencesSnapshot(9, ProfilePreferences()), false, null, {}, { saved += it })
            }
        }.use { ui ->
            ui.open("Audio language: Original audio")
            ui.type("jap")
            ui.click("Japanese (ja)")
            ui.open("Subtitle language: None")
            ui.type("spa")
            ui.click("Spanish (es)")
            ui.clickText("Save preferences")
            assertEquals(ProfilePreferences(preferred_audio_language = "ja", preferred_subtitle_language = "es"), saved.single())
        }
    }

    @Test fun settingsTitleAlignsWithTheNavBrandAtWideAndCollapsedWidths() {
        for (width in listOf(1920, 900)) {
            Ui(width, 1080) {
                Column(Modifier.fillMaxSize()) {
                    TopNavigation(Screen.Settings, television = false, navigate = {}, profile = Profile("1", "Viewer", null))
                    SettingsScreen(local = false, addons = emptyList(), enabledAddonIds = emptySet(), busy = false, error = null,
                        sessionNotice = null, install = {}, toggle = { _, _ -> }, remove = {}, profiles = {}, leave = {},
                        initialSection = "Subtitles & audio", browser = true,
                        sectionPreferences = { section, _ ->
                            ProfilePreferencesSettings(section, PreferencesSnapshot(9, ProfilePreferences()), false, null, {}, {})
                        })
                }
            }.use { ui ->
                val brand = ui.nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "top-nav-brand" }
                val title = ui.nodes().single { ui.text(it) == "Settings" }
                assertEquals(brand.boundsInRoot.left, title.boundsInRoot.left, 1f, "at $width dp")
            }
        }
    }

    private class Ui(private val width: Int, private val height: Int, private val content: @Composable () -> Unit) : AutoCloseable {
        private var frame = 0L
        private val scene = ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme(television = false, pointer = true) { Surface(Modifier.fillMaxSize(), color = Palette.background) { content() } }
        }.also { s -> repeat(20) { s.render(frame++ * 16_666_667L).close(); Thread.sleep(2) } }

        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(2) }
        fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        fun triggers() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
            .sortedBy { it.boundsInRoot.top }.map(::text)
        fun switch(label: String) = nodes().single {
            text(it).startsWith(label) && it.config.contains(SemanticsProperties.ToggleableState) && it.config.contains(SemanticsActions.OnClick)
        }
        private fun options() = nodes().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }.sortedBy { it.boundsInRoot.top }
        private fun optionLabel(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().first().text
        fun open(label: String) {
            val trigger = nodes().single { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList && text(it) == label }
            assertTrue(trigger.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        fun type(query: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            frames(10)
        }
        fun click(label: String) {
            val option = options().singleOrNull { optionLabel(it) == label } ?: fail("no option '$label' in ${options().map(::optionLabel)}")
            assertTrue(option.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        fun clickText(label: String) {
            val node = nodes().singleOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                ?: fail("no clickable '$label'")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
            frames(30)
        }
        override fun close() = scene.close()
    }
}