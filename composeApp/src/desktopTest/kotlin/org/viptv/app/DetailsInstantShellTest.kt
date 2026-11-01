package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.viptv.app.connection.*
import org.viptv.app.features.details.DetailsScreen
import org.viptv.app.state.*
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DetailsInstantShellTest {
    @get:Rule val uiThread = UiThreadRule()

    private val show = "tt0903747"
    private fun series() = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Breaking Bad", badges = emptyList())
    private fun episode(number: Long) = series().copy(
        identity = MediaIdentity("$show:1:$number", "series", emptyList(), EpisodeCoordinates("aired", 1, number)),
        parent_identity = series().identity, title = "Episode $number")
    private val full = DetailSnapshot(series(), episodes = listOf(episode(1)), recommendations = listOf(series().copy(identity = MediaIdentity("tt2", "series", emptyList()), title = "Better Call Saul")))

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }

    private fun ImageComposeScene.shows(value: String) = nodes(this).any { text(it) == value }
    private fun ImageComposeScene.loadingSkeleton() = nodes(this).any {
        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { label -> label == "Loading title details" } }

    @Test fun phoneShowsTheCardShellOnTheFirstFrameAndSwapsInTheMeta() {
        var detail by mutableStateOf<DetailSnapshot?>(DetailSnapshot(series(), episodes = emptyList()))
        var busy by mutableStateOf(true)
        var frame = 0L
        val scene = ImageComposeScene(390, 844, Density(1f)) { ViptvTheme(false) {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                DetailsScreen(detail, busy, error = null, back = {}, sources = {})
            }
        } }
        try {
            repeat(6) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue(scene.shows("Breaking Bad"), "the card's title is on the first frame")
            assertTrue(scene.shows("Play"), "the card's Play is on the first frame")
            assertTrue(scene.loadingSkeleton(), "the meta-only parts are skeletons while the read is in flight")

            busy = false; detail = full
            repeat(24) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
            assertTrue(scene.shows("Breaking Bad"), "the hero stays while the meta swaps in")
            assertTrue(scene.shows("1. Episode 1"), "the episode list replaces the skeleton")
            assertFalse(scene.loadingSkeleton(), "the skeleton is gone once the meta arrives")
        } finally { scene.close() }
    }

    @Test fun openDrawsTheShellFromTheCardAndReadsTheMetaOnce() = runBlocking<Unit> {
        val fixture = LocalSetupFixture()
        val card = series()
        var reads = 0
        val gate = CompletableDeferred<Unit>()
        val device = object : LocalConnection by fixture.local {
            override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot {
                reads++
                gate.await()
                return full
            }
        }
        val controller = fixture.controller { device }
        controller.local = device
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "default", localMode = true, preferences = PreferencesSnapshot())
        try {
            controller.open(card)
            val shell = controller.state.value
            assertEquals(Screen.Details, shell.screen)
            assertTrue(shell.detailShell, "the page is the card's shell while loading")
            assertEquals(card, shell.detail?.item)
            withTimeout(2000) { while (reads == 0) delay(1) }
            assertEquals(1, reads, "one meta read per open")

            gate.complete(Unit)
            withTimeout(5000) { controller.state.first { !it.busy } }
            val loaded = controller.state.value
            assertFalse(loaded.detailShell)
            assertEquals(1, loaded.detail?.episodes?.size)
            assertEquals(1, reads)
        } finally { gate.complete(Unit); fixture.close(controller) }
    }
}