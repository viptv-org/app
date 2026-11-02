package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.viptv.app.features.player.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class PlayerSeekFeedbackUiTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun aSeekKeepsThePlaybackIndicatorUntilPlaybackSettles() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(facts) { scene ->
            frames(scene)
            assertFalse(spinning(scene), "playback with no pending seek shows no indicator")
            seekAhead(scene); frames(scene, 20)
            assertTrue(spinning(scene), "a seek that is still waiting shows the indicator")
            assertTrue(facts.tryEmit(PlayerFact.Status("Buffering"))); frames(scene, 40)
            assertTrue(spinning(scene), "the platform still waiting keeps the indicator")
            assertTrue(facts.tryEmit(PlayerFact.Status("Playing"))); frames(scene, 60)
            assertFalse(spinning(scene), "resumed playback clears the indicator")
        }
    }

    @Test fun aReplacedSourceKeepsTheIndicatorUntilTheNewSourcePresentsAFrame() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(facts) { scene ->
            frames(scene)
            seekAhead(scene); frames(scene, 20)
            frames(scene, 120)
            assertTrue(spinning(scene), "a seek whose source is being replaced never reports settled playback")
            assertTrue(facts.tryEmit(PlayerFact.Presentation(PresentationProof.BrowserCompositorPresented))); frames(scene, 140)
            assertFalse(spinning(scene), "a presented frame ends the seek")
        }
    }

    @Test fun repeatedSeeksAndAResumedClockResolveTheIndicator() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(facts) { scene ->
            frames(scene)
            seekAhead(scene); frames(scene, 20)
            seekAhead(scene); frames(scene, 40)
            assertTrue(spinning(scene), "a repeated seek replaces the target and keeps waiting")
            assertTrue(facts.tryEmit(PlayerFact.Progress(130_000, 600_000))); frames(scene, 60)
            assertTrue(spinning(scene), "the seek target alone is not playback")
            assertTrue(facts.tryEmit(PlayerFact.Progress(141_000, 600_000))); frames(scene, 80)
            assertFalse(spinning(scene), "a clock past the target clears the indicator")
        }
    }

    @Test fun aFailedSeekClearsTheIndicator() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(facts) { scene ->
            frames(scene)
            seekAhead(scene); frames(scene, 20)
            assertTrue(spinning(scene))
            assertTrue(facts.tryEmit(PlayerFact.Failed)); frames(scene, 40)
            assertFalse(spinning(scene), "a failed seek never leaves a spinner behind")
        }
    }

    private fun scene(facts: MutableSharedFlow<PlayerFact>, body: (ImageComposeScene) -> Unit) {
        val scene = ImageComposeScene(844, 390, Density(1f)) { ViptvTheme {
            PlayerScreen(null, true, null, {}, {}, {}, phone = true, preview = PlayerFact.Progress(120_000, 600_000),
                playbackKey = "playing", externalFacts = facts)
        } }
        try { body(scene) } finally { scene.close() }
    }

    private fun spinning(scene: ImageComposeScene) = nodes(scene).any {
        it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate }

    private fun seekAhead(scene: ImageComposeScene) {
        assertTrue(nodes(scene).single {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { description -> description.contains("Forward 10 seconds") } == true &&
                it.config.contains(SemanticsActions.OnClick)
        }.config[SemanticsActions.OnClick].action!!.invoke(), "the transport seeks ahead")
    }

    private fun frames(scene: ImageComposeScene, start: Int = 0) = repeat(16) { scene.render((start + it) * 16_666_667L).close(); Thread.sleep(4) }
    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
