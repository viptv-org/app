package org.viptv.app

import java.io.File
import kotlin.test.*

// AND-KEYBOARD-001: every TV text entry is an editable field the device IME and a connected keyboard type into.
class TvTextEntryScanTest {
    private val root = File("src/commonMain/kotlin/org/viptv/app")
    private fun sources() = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun tvSources() = sources().filter { it.name.startsWith("Tv") }

    private val retired = listOf("TvEntryField", "TvEntryKeyboard", "TvEntryKey", "TvSearchKeyboard", "TvKeyboardKey",
        "TvPinKey", "Jump to results", "Delete character")

    private val fieldConstruct = Regex("""\b(AppTextField|AppBasicTextField|BasicTextField|OutlinedTextField)\(""")

    private val sharedFields = setOf("TvTextField.kt", "TvDialogPanel.kt")

    @Test fun theAppRendersNoCharacterOrPinGridOnTv() {
        assertTrue(root.isDirectory, "run from the composeApp module: ${root.absolutePath}")
        val found = tvSources().flatMap { file ->
            val text = file.readText()
            retired.filter { Regex("""(?<![A-Za-z0-9_])${Regex.escape(it)}(?![A-Za-z0-9_])""").containsMatchIn(text) }
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList(), found, "TV text entry is the shared editable field, not an app key grid")
    }

    @Test fun everyTvFieldIsTheSharedEditableField() {
        val found = tvSources().filter { it.name !in sharedFields }
            .filter { fieldConstruct.containsMatchIn(it.readText()) }
            .map { it.name }
        assertEquals(emptyList(), found,
            "TV pages type into components/TvTextField.kt and dialogs into TvDialogField, never into a field of their own")
    }

    @Test fun noTvFieldPaintsItsValueBehindAFakeCaret() {
        // The bug's tell: a painted value with a narrow accent bar where the real caret would be.
        val caret = Regex("""Box\([^\n]*\.(?:width|size)\(\s*[23](?:\.0)?f?\.?[^)\n]*\)[^\n]*\.background\(Palette\.accent\)""")
        val found = tvSources().filter { caret.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList(), found, "a focused TV field shows the real caret of an editable node")
    }

    @Test fun everyTvTextEntrySurfaceUsesTheSharedField() {
        val surfaces = mapOf(
            "features/search/TvSearchScreen.kt" to "TvTextField(",
            "features/entry/TvPasswordPanel.kt" to "TvTextField(",
            "features/entry/TvPinScreen.kt" to "TvTextField(",
            "features/settings/TvAddonEntry.kt" to "TvTextField(",
            "features/settings/TvAddonSettings.kt" to "TvTextField(",
            "features/settings/TvSourceIntegrationForm.kt" to "TvTextField(",
            "features/settings/TvXtreamConnections.kt" to "TvTextField(",
            "features/settings/TvVodMatchesSettings.kt" to "TvTextField(")
        for ((name, field) in surfaces) assertTrue(File(root, name).readText().contains(field), "$name types into $field")
        assertTrue(File(root, "features/search/TvSearchScreen.kt").readText().contains("\"Results\""),
            "Search carries its visible Results action")
    }
}