package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.features.player.PlayerRoute
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerFailureOwnershipUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("full-dive", "episode", emptyList(), EpisodeCoordinates("aired", 1, 4)), provider_id = "addon:7",
        title = "Episode 4", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Torrentio TB 1080p")
    private val headline = "This source stopped working"

    @Test fun aGlobalErrorDuringPlaybackNeverDrawsTheFailurePage() {
        for ((name, layout) in layouts) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val controller = ApplicationController(scope, store)
            controller.mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = "profile", sourceTitle = title,
                sources = listOf(source), playingSource = source, preferences = PreferencesSnapshot(),
                presentation = PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted),
                error = "Could not load the next episode.")
            val scene = ImageComposeScene(layout.width, layout.height, Density(1f)) { ViptvTheme(television = layout.television) {
                val state by controller.state.collectAsState()
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    PlayerRoute(state, controller, layout.television, layout.phone, browser = false, backChanged = {})
                }
            } }
            try {
                repeat(20) { scene.render(it * 16_666_667L).close() }
                assertFalse(headline in texts(scene), "$name: an unrelated error leaves the player alone; saw ${texts(scene)}")
                controller.mutable.value = controller.state.value.copy(playbackFailure = PlaybackFailure.Failed)
                scene.renderUntil(what = "$name failure page") { headline in texts(scene) }
                assertTrue(texts(scene).any { "Torrentio" in it }, "$name: the page names the playing source; saw ${texts(scene)}")
            } finally { scene.close(); scope.cancel() }
        }
    }

    private class Layout(val width: Int, val height: Int, val phone: Boolean, val television: Boolean)
    private val layouts = mapOf(
        "phone" to Layout(844, 390, phone = true, television = false),
        "television" to Layout(1920, 1080, phone = false, television = true))

    private fun texts(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }.map(::text)
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
