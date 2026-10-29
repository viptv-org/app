package org.viptv.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.viptv.app.connection.*
import org.viptv.app.features.settings.*
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PreferenceSettingsUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val original = ProfilePreferences("cs-CZ", "uk", true, "best", 480, 1.1, "#AA1122", "#60223344")

    @Test fun tvPreferenceFailureFocusesRetryDisablesDuplicatesAndRestoresLoadedControls() {
        var preferences by mutableStateOf<TvSettingsPreferences?>(null)
        var error by mutableStateOf<String?>("Could not load profile preferences. Try again.")
        var busy by mutableStateOf(false)
        var retried = 0; var changed = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            SettingsScreen(false, emptyList(), emptySet(), busy, null, null, {}, { _, _ -> }, {}, {}, {}, television = true,
                tvPreferences = preferences, changeTvPreferences = { changed++ }, preferencesError = error,
                retryPreferences = { retried++; busy = true })
        } }
        try {
            frames(scene); assertEquals(true, action(scene, "Retry settings").config.getOrNull(SemanticsProperties.Focused))
            assertTrue(nodes(scene).any { text(it) == error }); capture(scene, "tv-preference-load-failure")
            click(scene, "Retry settings"); frames(scene, 20); assertEquals(1, retried)
            assertTrue(action(scene, "Loading settings…").config.contains(SemanticsProperties.Disabled)); assertEquals(0, changed)
            preferences = TvSettingsPreferences(source = "Always ask"); error = null; busy = false; frames(scene, 40)
            assertFalse(nodes(scene).any { text(it) == "Retry settings" })
            assertTrue(nodes(scene).any { text(it).startsWith("Default source") && text(it).contains("Always ask") })
            assertFalse(nodes(scene).any { text(it).startsWith("Next episode button") || text(it).startsWith("Match frame rate") })
            assertEquals(true, nodes(scene).single { text(it).startsWith("Default source") && it.config.contains(SemanticsActions.OnClick) }
                .config.getOrNull(SemanticsProperties.Focused))
            assertEquals(0, changed)
        } finally { scene.close() }
    }

    @Test fun tvSubtitleFailureUsesItsOwnSectionAndRetryKeepsTheSafeError() {
        var retried = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) { ViptvTheme(true) {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, television = true,
                initialSection = "Subtitles & audio", preferencesError = "Could not load profile preferences. Try again.", retryPreferences = { retried++ })
        } }
        try {
            frames(scene); click(scene, "Retry settings"); assertEquals(1, retried)
            assertTrue(nodes(scene).any { text(it) == "Could not load profile preferences. Try again." })
            assertFalse(nodes(scene).any { text(it) == "Subtitle style preview" || text(it) == "Next episode button" })
        } finally { scene.close() }
    }

    @Test fun phonePlaybackDraftUsesActualSourceQualityAndPreservesOtherFieldsUntilSave() {
        val saved = mutableListOf<ProfilePreferences>()
        val scene = phone("Playback", save = { saved += it })
        try {
            frames(scene); assertFalse(nodes(scene).any { text(it) == "Playback preferences" || text(it) == "Skip intro button" })
            click(scene, "Default source Best match"); frames(scene, 20); click(scene, "Cancel"); frames(scene, 40)
            assertTrue(saved.isEmpty())
            click(scene, "Default source Best match"); frames(scene, 60); click(scene, "Always ask"); frames(scene, 80)
            assertTrue(saved.isEmpty()); click(scene, "Save preferences"); assertEquals(original.copy(source_selection = "ask"), saved.single())
            click(scene, "Preferred quality 480p"); frames(scene, 100); click(scene, "4K"); frames(scene, 120)
            capture(scene, "phone-playback-preferences"); click(scene, "Save preferences")
            assertEquals(original.copy(source_selection = "ask", max_quality_height = 2160), saved.last())
        } finally { scene.close() }
    }

    @Test fun phoneSubtitlePreviewAndSaveRetainAllEightFieldsWithCustomLanguagesAndAlpha() {
        var saved: ProfilePreferences? = null
        val scene = phone("Subtitles & audio", save = { saved = it })
        try {
            frames(scene); click(scene, "L"); colour(scene, "Text colour Yellow"); click(scene, "Subtitles Always"); frames(scene, 10); click(scene, "Never"); frames(scene, 20)
            click(scene, "Audio language cs-CZ"); frames(scene, 40); type(scene, "Language tag", "pt-BR"); frames(scene, 60)
            click(scene, "Use value"); frames(scene, 80)
            click(scene, "Custom background"); frames(scene, 100); type(scene, "Background colour", "#40223344"); frames(scene, 120)
            click(scene, "Use value"); frames(scene, 140)
            assertNull(saved)
            val results = mutableListOf<TextLayoutResult>()
            nodes(scene).single { text(it) == "I didn’t hear you come in." }.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            assertEquals(22.5f, results.single().layoutInput.style.fontSize.value)
            assertEquals(phonePreferenceColor("#FFE16B"), results.single().layoutInput.style.color)
            capture(scene, "phone-subtitle-preferences"); click(scene, "Save preferences")
            assertEquals(original.copy(preferred_audio_language = "pt-BR", subtitles_enabled = false, subtitle_mode = "never", subtitle_font_scale = 1.25,
                subtitle_text_color = "#FFE16B", subtitle_background_color = "#40223344"), saved)
        } finally { scene.close() }
    }

    @Test fun phoneSettingsRoutesPlaybackAndSubtitlesToDistinctSections() {
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {},
                sectionPreferences = { section, compact -> assertTrue(compact); PhoneProfilePreferences(section, PreferencesSnapshot(9, original), false, null, {}, {}) })
        } }
        try {
            frames(scene); click(scene, "Playback"); frames(scene, 20)
            assertTrue(nodes(scene).any { text(it) == "Sources" }); assertFalse(nodes(scene).any { text(it) == "Size" })
            click(scene, "Back"); frames(scene, 40); click(scene, "Subtitles & audio"); frames(scene, 60)
            assertTrue(nodes(scene).any { text(it) == "Size" }); assertFalse(nodes(scene).any { text(it) == "Sources" })
        } finally { scene.close() }
    }

    @Test fun phoneRetryAndInvalidCustomValuesCannotInventOrSubmitPreferences() {
        var snapshot by mutableStateOf<PreferencesSnapshot?>(null); var busy by mutableStateOf(false)
        var retry = 0; var saved = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhoneProfilePreferences("Subtitles & audio", snapshot, busy, if (snapshot == null) "Could not load profile preferences. Try again." else null,
                { retry++; busy = true }, { saved++ })
        } }
        try {
            frames(scene); click(scene, "Retry settings"); frames(scene, 20); assertEquals(1, retry)
            assertTrue(action(scene, "Retry settings").config.contains(SemanticsProperties.Disabled)); assertEquals(0, saved)
            snapshot = PreferencesSnapshot(9, original); busy = false; frames(scene, 40)
            click(scene, "Audio language cs-CZ"); frames(scene, 60); type(scene, "Language tag", "not a tag"); frames(scene, 80)
            assertTrue(action(scene, "Use value").config.contains(SemanticsProperties.Disabled)); click(scene, "Cancel"); frames(scene, 100)
            assertTrue(nodes(scene).any { text(it) == "Audio language cs-CZ" }); assertEquals(0, saved)
            assertTrue(action(scene, "Save preferences").config.contains(SemanticsProperties.Disabled))
        } finally { scene.close() }
    }

    @Test fun languageDialogKeepsActionsAboveKeyboardAndScrollsChoicesInCompactViewport() {
        var updated: ProfilePreferences? = null; var cancelled = 0
        val keyboardHeight = 320
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme {
            PhonePreferenceDialog(PhonePreferenceField.AudioLanguage, original, false, { updated = it }, { cancelled++ },
                keyboardInsets = WindowInsets(bottom = keyboardHeight))
        } }
        try {
            frames(scene)
            val visibleBottom = 844f - keyboardHeight
            for (label in listOf("Cancel", "Use value")) {
                val bounds = action(scene, label).boundsInRoot
                assertTrue(bounds.top >= 0f && bounds.bottom <= visibleBottom, "$label hidden below keyboard: $bounds")
            }
            val body = nodes(scene).single { it.config.contains(SemanticsActions.ScrollBy) }
            assertTrue(body.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 1000f)); frames(scene, 20)
            val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.boundsInRoot.height >= 48f, "Custom language field cannot be reached by scrolling")
            assertTrue(field.boundsInRoot.bottom <= action(scene, "Use value").boundsInRoot.top)
            type(scene, "Language tag", "pt-BR"); frames(scene, 40)
            click(scene, "Use value"); assertEquals(original.copy(preferred_audio_language = "pt-BR"), updated)
            assertEquals(1, cancelled)
            capture(scene, "phone-language-dialog-keyboard")
        } finally { scene.close() }
    }

    private fun phone(section: String, save: (ProfilePreferences) -> Unit) = ImageComposeScene(390, 844, Density(1f)) {
        ViptvTheme { PhoneProfilePreferences(section, PreferencesSnapshot(9, original), false, null, {}, save) }
    }
    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun action(scene: ImageComposeScene, label: String) = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
    private fun click(scene: ImageComposeScene, label: String) { assertTrue(action(scene, label).config[SemanticsActions.OnClick].action!!.invoke()) }
    private fun colour(scene: ImageComposeScene, label: String) { nodes(scene).single {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true && it.config.contains(SemanticsActions.OnClick)
    }.config[SemanticsActions.OnClick].action!!.invoke() }
    private fun type(scene: ImageComposeScene, label: String, value: String) {
        val field = nodes(scene).single { it.config.contains(SemanticsActions.SetText) && text(it).contains(label) }
        assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
    }
    private fun capture(scene: ImageComposeScene, name: String) { System.getenv("VIPTV_PREFERENCES_CAPTURE_DIR")?.let { path ->
        File(path).mkdirs(); scene.render(4_000_000_000L).use { image -> image.encodeToData()!!.use { File(path, "$name.png").writeBytes(it.bytes) } }
    } }
}
