package org.viptv.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.features.settings.TvSettingsScreen
import org.viptv.app.features.settings.TvSettingsSection
import org.viptv.app.features.settings.TvVodMatchesSettings
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.VodMatchForm
import org.viptv.app.state.VodMatchProvider
import org.viptv.app.state.VodMatchRow
import org.viptv.app.state.VodMatchesState
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvVodMatchesFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private var focusManager: FocusManager? = null
    private var frame = 0L

    private fun page() = VodMatchesState(
        providers = listOf(VodMatchProvider(1, "Home Xtream")),
        rows = listOf(VodMatchRow("vod:1:1", 1, "movie", "Star Wars", 1977),
            VodMatchRow("vod:1:2", 1, "series", "The Wire", 2002)),
        loaded = true)

    @Test fun dpadReachesTheFirstRowTheFiltersAndTheMatchForm() = runBlocking<Unit> {
        val state = mutableStateOf(page())
        var saveCalls = 0
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                focusManager = LocalFocusManager.current
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null,
                    notice = null, install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.VodMatches,
                    vodMatches = { first, sidebar, scale ->
                        TvVodMatchesSettings(state.value, scale, first, sidebar,
                            providerChanged = {}, typeChanged = {}, search = {}, openForm = { row ->
                                state.value = state.value.copy(form = VodMatchForm(row.vodId, row.type, row.name, "Home Xtream"))
                            }, loadMore = {}, retry = {},
                            closeForm = { state.value = state.value.copy(form = null) },
                            changeMetadata = { value ->
                                state.value = state.value.copy(form = state.value.form?.copy(metadataId = value))
                            }, save = { saveCalls++ })
                    })
            }
        }
        try {
            settle(scene)
            val firstRow = requireNotNull(focused(scene)) { "nothing focused" }
            assertTrue(text(firstRow).contains("Star Wars"), "the first unmatched title takes focus, not ${text(firstRow)}")

            press(scene, Key.DirectionDown)
            assertTrue(text(requireNotNull(focused(scene))).contains("The Wire"), "Down walks the list")
            press(scene, Key.DirectionUp)
            assertTrue(text(requireNotNull(focused(scene))).contains("Star Wars"))

            press(scene, Key.DirectionUp)
            val chip = text(requireNotNull(focused(scene)))
            assertTrue(listOf("Search", "All providers", "Home Xtream", "Movies", "Series").any { chip == it },
                "Up reaches the filter chips, focused $chip")
            press(scene, Key.DirectionDown)
            assertTrue(text(requireNotNull(focused(scene))).contains("Star Wars"), "Down returns to the first row")

            activate(scene) { state.value.form != null }
            assertEquals("vod:1:1", state.value.form?.vodId)
            val form = nodes(scene)
            assertTrue(form.any { text(it) == "Match title" }, "Enter opens the match form")
            assertTrue(form.any { text(it) == "Star Wars" })
            assertTrue(form.any { text(it) == "Metadata ID" })
            assertTrue(form.any { text(it) == "Save match" && it.config.contains(SemanticsActions.OnClick) })
            val inForm = requireNotNull(focused(scene)) { "the form keeps a focus stop" }
            assertFalse(text(inForm).contains("Star Wars"), "focus moves into the form, not the row behind it")

            activate(scene) { state.value.form?.metadataId == "q" }
            assertEquals("q", state.value.form?.metadataId, "the on-screen keyboard types into the metadata ID")

            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.Escape, KeyEventType.KeyUp))
            settle(scene)
            assertNull(state.value.form, "Escape closes the form")
            assertTrue(nodes(scene).none { text(it) == "Metadata ID" })
            assertTrue(text(requireNotNull(focused(scene))).contains("Star Wars"), "focus returns to the row that opened the form")
            assertEquals(0, saveCalls)
        } finally { scene.close() }
    }

    @Test fun anEmptyListOpensOnTheSearchChip() = runBlocking<Unit> {
        val state = VodMatchesState(providers = listOf(VodMatchProvider(1, "Home Xtream")), loaded = true)
        val scene = ImageComposeScene(1920, 1080, Density(1f)) {
            ViptvTheme(true) {
                focusManager = LocalFocusManager.current
                TvSettingsScreen(local = false, addons = emptyList(), enabled = emptySet(), busy = false, error = null,
                    notice = null, install = {}, toggle = { _, _ -> }, profiles = {}, leave = {}, profile = PreviewData.profile,
                    initialSection = TvSettingsSection.VodMatches,
                    vodMatches = { first, sidebar, scale ->
                        TvVodMatchesSettings(state, scale, first, sidebar, {}, {}, {}, {}, {}, {}, {}, {}, {})
                    })
            }
        }
        try {
            settle(scene)
            assertTrue(text(requireNotNull(focused(scene))).contains("Search"), "an empty list starts on the search chip")
            assertTrue(nodes(scene).any { text(it) == "No unmatched titles." })
        } finally { scene.close() }
    }

    private suspend fun settle(scene: ImageComposeScene) = repeat(4) {
        repeat(8) { scene.render(frame++ * 16_666_667L).close() }
        delay(10)
    }

    private fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        repeat(6) { scene.render(frame++ * 16_666_667L).close() }
    }

    private fun activate(scene: ImageComposeScene, opened: () -> Boolean) {
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        repeat(6) { scene.render(frame++ * 16_666_667L).close() }
        if (opened()) return
        val action = nodes(scene).firstOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
            ?.config?.getOrNull(SemanticsActions.OnClick)?.action
        assertNotNull(action, "the focused control is activatable")
        action()
        repeat(6) { scene.render(frame++ * 16_666_667L).close() }
    }

    private fun focused(scene: ImageComposeScene) = nodes(scene).singleOrNull { it.config.getOrNull(SemanticsProperties.Focused) == true }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = listOf(
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty(),
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
}