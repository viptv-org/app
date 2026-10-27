package org.viptv.app

import org.junit.Rule
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.entry.ConnectScreen
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlaybackTrack
import org.viptv.app.player.PlaybackTracks
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

/**
 * app#45: the route steps that desktop and browser Back (GlobalBack: Escape, the mouse button, browser
 * history) run before navigateBack, in the real screens.
 */
@OptIn(ExperimentalComposeUiApi::class)
class RouteBackUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private var frame = 0L
    private fun ImageComposeScene.frames(count: Int = 16) = repeat(count) { render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun label(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
    private fun ImageComposeScene.shows(value: String) = nodes().any { value in label(it) }
    private fun ImageComposeScene.click(value: String) {
        val node = assertNotNull(nodes().firstOrNull { it.config.contains(SemanticsActions.OnClick) && flatten(it).any { child -> value in label(child) } },
            "No clickable \"$value\"")
        assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke()); frames(20)
    }

    @Test fun desktopAndBrowserPlayerBackClosesTheAudioAndSubtitlesMenuBeforeThePlayer() {
        var routeBack: (() -> Unit)? = null
        var closed = 0
        val facts = MutableSharedFlow<PlayerFact>(replay = 1)
        facts.tryEmit(PlayerFact.Tracks(PlaybackTracks(audio = listOf(PlaybackTrack(1, "English · 5.1", true, "en")),
            subtitles = listOf(PlaybackTrack(3, "Spanish", false, "es")))))
        val scene = ImageComposeScene(1280, 720, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, { closed++ }, title = org.viptv.engine.wire.Card(org.viptv.engine.wire.MediaIdentity("m", "movie", emptyList()),
                provider_id = "metadata", title = "Movie", badges = emptyList()), preview = PlayerFact.Progress(842_000, 3_132_000),
                phone = false, externalFacts = facts, backChanged = { routeBack = it })
        } }
        try {
            scene.frames()
            scene.click("Audio and subtitles")
            assertTrue(scene.shows("Audio & subtitles"), "the menu did not open")
            assertNotNull(routeBack, "the player registers no Back for desktop and browser").invoke(); scene.frames(20)
            assertFalse(scene.shows("Audio & subtitles"), "Back must close the menu first")
            assertEquals(0, closed, "Back closed the player under its open menu")
            routeBack!!.invoke(); scene.frames()
            assertEquals(1, closed)
        } finally { scene.close() }
    }

    @Test fun browserBackLeavesTheCreateAccountAndRecoveryFormsForSignIn() {
        var routeBack: (() -> Unit)? = null
        val scene = ImageComposeScene(1280, 800, Density(1f)) { ViptvTheme {
            ConnectScreen(browser = true, origin = "https://watch.example", busy = false, error = null, local = null, connect = { _, _, _ -> },
                backChanged = { routeBack = it }, register = { _, _, _ -> }, recover = { _, _, _ -> })
        } }
        try {
            scene.frames()
            assertNull(routeBack, "sign-in itself is the root: browser Back leaves the site")
            for (form in listOf("Create account", "Use recovery code")) {
                scene.click(form)
                assertFalse(scene.shows("Welcome back"), "$form did not open")
                assertNotNull(routeBack, "$form registers no Back").invoke(); scene.frames()
                assertTrue(scene.shows("Welcome back"), "Back from $form did not return to sign-in")
                assertNull(routeBack, "Back from $form left a route step behind")
            }
        } finally { scene.close() }
    }
}
