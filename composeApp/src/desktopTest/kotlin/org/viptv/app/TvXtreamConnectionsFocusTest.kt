package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.XtreamConnection
import org.viptv.app.connection.XtreamRefresh
import org.viptv.app.connection.XtreamScopes
import org.viptv.app.features.settings.SettingsScreen
import org.viptv.app.features.settings.TvSettingsPreferences
import org.viptv.app.features.settings.TvXtreamConnections
import org.viptv.app.features.settings.xtreamConnectionsSection
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvXtreamConnectionsFocusTest {
    @get:Rule val uiThread = UiThreadRule()

    private var focusManager: FocusManager? = null
    private var clock = 0L
    private val synced = mutableListOf<Long>()
    private val defaulted = mutableListOf<Long>()
    private val removed = mutableListOf<Long>()
    private val connections = listOf(
        XtreamConnection(1, "Living room", XtreamScopes(true, true, true, true), true, XtreamRefresh("succeeded", null)),
        XtreamConnection(2, "Bedroom", XtreamScopes(true, true, false, false), true, XtreamRefresh("failed", "The provider catalog could not be refreshed.")))

    private fun scene() = ImageComposeScene(1920, 1080, Density(1f)) {
        ViptvTheme(true) {
            focusManager = LocalFocusManager.current
            SettingsScreen(false, emptyList(), emptySet(), false, null, null, {}, { _, _ -> }, {}, {}, {}, null,
                television = true, initialSection = "Playback", tvPreferences = TvSettingsPreferences(), changeTvPreferences = { },
                tvXtreamConnections = { first, sidebar, scale ->
                    TvXtreamConnections(connections, 1L, false, null, null, canManage = true, manageNote = "", scale = scale,
                        first = first, sidebar = sidebar, request = {}, create = { _, _, _, _, _ -> }, update = { _, _, _ -> },
                        replacePassword = { _, _ -> }, sync = { synced += it }, setDefault = { defaulted += it },
                        remove = { removed += it })
                })
        }
    }

    @Test fun theRemoteWalksFromTheSidebarToTheConnectionsPageAndIntoOneConnectionsActions() = runBlocking {
        val scene = scene()
        try {
            frames(scene)
            val rail = nodes(scene).first { it.config.contains(SemanticsActions.OnClick) && text(it) == xtreamConnectionsSection }
            click(scene, xtreamConnectionsSection)
            frames(scene)
            assertFocused(scene, "Living room")

            press(scene, Key.DirectionDown)
            assertFocused(scene, "Bedroom")
            press(scene, Key.DirectionUp)
            assertFocused(scene, "Living room")
            press(scene, Key.DirectionLeft)
            assertTrue(focusedLabels(scene).any { it == xtreamConnectionsSection },
                "Left returns to the rail row that opened the page, was ${focusedLabels(scene)}")
            assertEquals(xtreamConnectionsSection, text(rail))
            press(scene, Key.DirectionRight)
            assertFocused(scene, "Living room")

            press(scene, Key.Enter)
            assertTrue(nodes(scene).any { text(it) == "Sync now" }, "Enter opens the connection's actions")
            assertFocused(scene, "Sync now")
            press(scene, Key.Enter)
            frames(scene)
            assertEquals(listOf(1L), synced, "the focused action runs the sync")

            press(scene, Key.DirectionDown)
            assertFocused(scene, "Edit name and content")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Replace password")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Remove connection")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Back")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Back")
        } finally { scene.close() }
    }

    @Test fun editingAConnectionReachesItsFieldsAndScopeToggles() = runBlocking {
        val scene = scene()
        try {
            frames(scene)
            click(scene, xtreamConnectionsSection); frames(scene)
            assertFocused(scene, "Living room")
            press(scene, Key.Enter)
            assertFocused(scene, "Sync now")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Edit name and content")
            press(scene, Key.Enter)
            assertTrue(nodes(scene).any { text(it) == "Save" }, "Edit mode shows its Save")
            assertTrue(nodes(scene).any { text(it) == "Connection name" }, "the name field is labelled")
            assertFocused(scene, "Living room")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Connection enabled")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Live TV")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Movies")
            press(scene, Key.DirectionDown)
            assertFocused(scene, "Series")
            press(scene, Key.DirectionDown)
            val actions = focusedLabels(scene)
            assertTrue(actions.any { it == "Save" || it == "Cancel" }, "Down from the scopes reaches the form actions, was $actions")
        } finally { scene.close() }
    }

    @Test fun removingAConnectionAsksBeforeSendingAnything() = runBlocking {
        val scene = scene()
        try {
            frames(scene)
            click(scene, xtreamConnectionsSection); frames(scene)
            press(scene, Key.DirectionDown)
            press(scene, Key.Enter)
            assertFocused(scene, "Sync now")
            for (step in 1..4) press(scene, Key.DirectionDown)
            assertFocused(scene, "Remove connection")
            press(scene, Key.Enter)
            frames(scene)
            assertFocused(scene, "Remove")
            assertTrue(removed.isEmpty(), "nothing is removed before the confirmation")
            press(scene, Key.Enter)
            frames(scene)
            assertEquals(listOf(2L), removed)
        } finally { scene.close() }
    }

    private suspend fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(clock++ * 16_666_667L).close(); delay(4) }
    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun text(node: SemanticsNode): String = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun focusedLabels(scene: ImageComposeScene): List<String> =
        nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.map(::text)
    private suspend fun assertFocused(scene: ImageComposeScene, label: String) {
        frames(scene)
        val now = focusedLabels(scene)
        assertTrue(now.any { it.startsWith(label) }, "expected focus on $label, was $now")
    }
    private suspend fun press(scene: ImageComposeScene, key: Key) {
        val consumed = scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        if (!consumed && key != Key.Enter) requireNotNull(focusManager).moveFocus(when (key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            Key.DirectionLeft -> FocusDirection.Left
            else -> FocusDirection.Right
        })
        frames(scene)
    }
    private suspend fun click(scene: ImageComposeScene, label: String) {
        val node = nodes(scene).first { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
    }
}
