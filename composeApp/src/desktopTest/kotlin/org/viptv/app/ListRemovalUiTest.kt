package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.ListRemovalHost.Companion.continueCard
import org.viptv.app.ListRemovalHost.Companion.libraryCard
import org.viptv.app.components.ListRemoval
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.continuewatching.ContinueWatchingRoute
import org.viptv.app.features.continuewatching.ContinueWatchingScreen
import org.viptv.app.features.home.CardMenu
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.state.*
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ListRemovalUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
    private fun clickable(scene: ImageComposeScene, text: String) = nodes(scene).firstOrNull {
        it.config.contains(SemanticsActions.OnClick) && (label(it).contains(text) || it.children.any { child -> label(child).contains(text) })
    }
    private fun tap(scene: ImageComposeScene, text: String) =
        assertTrue(requireNotNull(clickable(scene, text)) { "$text is not on screen" }
            .config[SemanticsActions.OnClick].action?.invoke() == true)
    private fun frames(scene: ImageComposeScene, count: Int = 8) = repeat(count) {
        scene.render(it * 16_666_667L).close(); Thread.sleep(4)
    }
    private fun withScene(width: Int, content: @Composable () -> Unit, test: (ImageComposeScene) -> Unit) {
        val scene = ImageComposeScene(width, 900, Density(1f)) { ViptvTheme { Box(Modifier.fillMaxSize()) { content() } } }
        try { test(scene) } finally { scene.close() }
    }
    private fun moreActions(scene: ImageComposeScene) = nodes(scene).count { label(it) == "More actions" }

    @Test fun aCardWithoutACardMenuGetsItsOwnMenuTargetOnPhoneAndWeb() {
        for (width in listOf(390, 1280)) {
            withScene(width, {
                LibraryScreen(listOf(libraryCard(1)), available = true, television = false, open = {}, history = {},
                    home = {}, remove = {})
            }) { scene ->
                frames(scene)
                assertEquals(1, moreActions(scene), "$width: the card offers its menu to touch and the pointer")
                tap(scene, "More actions")
            }
            withScene(width, {
                LibraryScreen(listOf(libraryCard(1)), available = true, television = false, open = {}, history = {}, home = {})
            }) { scene ->
                frames(scene)
                assertEquals(0, moreActions(scene), "$width: a card with no menu of its own draws no target")
            }
        }
    }

    @Test fun theCardMenuOffersTheRemovalInsteadOfTheAmbiguousSaveToggle() {
        var requested = 0
        val scene = ImageComposeScene(400, 500, Density(1f)) { ViptvTheme(pointer = true) {
            Box(Modifier.fillMaxSize()) {
                CardMenu(libraryCard(1), saved = true,
                    removal = ListRemoval("Remove from My List", replacesSave = true) { requested++ }, actions = null,
                    onDismiss = {}, onPlay = {}, onInfo = {}, onToggleSave = {})
            }
        } }
        try {
            frames(scene)
            assertNotNull(clickable(scene, "More info"), "the menu keeps More info")
            assertNull(clickable(scene, "In My List"), "the removal replaces the toggle that said the same thing")
            tap(scene, "Remove from My List")
            assertEquals(1, requested)
        } finally { scene.close() }
    }

    @Test fun theCardMenuKeepsTheSaveToggleWhereNothingIsBeingRemoved() {
        val scene = ImageComposeScene(400, 500, Density(1f)) { ViptvTheme(pointer = true) {
            Box(Modifier.fillMaxSize()) {
                CardMenu(libraryCard(1), saved = true, removal = null, actions = null, onDismiss = {}, onPlay = {},
                    onInfo = {}, onToggleSave = {})
            }
        } }
        try {
            frames(scene)
            assertNotNull(clickable(scene, "In My List"), "Home and catalog cards keep their save toggle")
            assertNull(clickable(scene, "Remove from My List"))
        } finally { scene.close() }
    }

    @Test fun theRemovedNoticeUndoesInPlace() {
        var undone = 0
        withScene(1280, {
            ContinueWatchingScreen(ContinueWatchingPage(listOf(continueCard(1), continueCard(2)), loaded = true),
                play = {}, open = {}, more = null, retry = {}, back = {}, home = {}, remove = {},
                undo = ContinueUndo("1", continueCard(2), 1), undoRemove = { undone++ })
        }) { scene ->
            frames(scene)
            assertTrue(nodes(scene).any { label(it).contains("Removed “Show 02” from Continue Watching.") },
                "the notice names what left the list")
            tap(scene, "Undo")
            frames(scene)
            assertEquals(1, undone)
        }
    }

    @Test fun localModeOffersNoContinueWatchingRemoval() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ListRemovalHost().use { host ->
            val controller = ApplicationController(scope, host.sessionStore)
            try {
                for ((local, expected) in listOf(true to 0, false to 1)) {
                    controller.mutable.value = ApplicationState(screen = Screen.ContinueWatching, selectedProfile = "1",
                        localMode = local, preferences = PreferencesSnapshot(),
                        continueWatching = ContinueWatchingList("1", listOf(continueCard(1)), next = null))
                    val scene = ImageComposeScene(1280, 900, Density(1f)) { ViptvTheme {
                        ContinueWatchingRoute(controller.state.value, controller, television = false)
                    } }
                    try {
                        frames(scene)
                        assertEquals(expected, moreActions(scene),
                            if (local) "local mode has no visibility endpoint, so it offers no removal" else "remote mode does")
                    } finally { scene.close() }
                }
            } finally { scope.cancel() }
        }
    }
}
