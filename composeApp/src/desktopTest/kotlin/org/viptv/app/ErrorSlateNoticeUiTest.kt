package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.features.player.TvPlaybackExtras
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ErrorSlateNoticeUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val title = Card(MediaIdentity("slate-title", "movie", emptyList()), provider_id = "addon:7", title = "A Movie",
        subtitle = "2026 · 1h 52m", badges = emptyList())

    private class Clicks { var configure = 0; var choose = 0 }

    @Test fun desktopShowsTheNoticeOverPlaybackAndOffersBothActions() {
        val clicks = Clicks()
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(1440, 900) { PlayerScreen(null, false, null, {}, {}, {}, title, externalFacts = facts,
            slateProvider = "ElfHosted", configureSource = { clicks.configure++ }, chooseSources = { clicks.choose++ }) }
            .use { scene ->
                frames(scene)
                assertFalse(texts(scene).contains("This source might not be working."), "nothing is suspected yet")
                assertTrue(facts.tryEmit(PlayerFact.ProbableErrorSlate))
                frames(scene)
                assertTrue(texts(scene).contains("This source might not be working."), "the notice appears: ${texts(scene)}")
                assertTrue(texts(scene).contains("ElfHosted handed back this video. Its settings may need a new key."))
                click(scene, "Configure source"); click(scene, "Choose another source")
                assertEquals(1, clicks.configure); assertEquals(1, clicks.choose)
            }
    }

    @Test fun phoneKeepsBothActionsOnScreenAtItsWidth() {
        val clicks = Clicks()
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(390, 844) { PlayerScreen(null, false, null, {}, {}, {}, title, phone = true, externalFacts = facts,
            configureSource = { clicks.configure++ }, chooseSources = { clicks.choose++ }) }
            .use { scene ->
                frames(scene)
                assertTrue(facts.tryEmit(PlayerFact.ProbableErrorSlate))
                frames(scene)
                assertTrue(texts(scene).contains("This source might not be working."), "the notice appears: ${texts(scene)}")
                assertTrue(texts(scene).contains("This source's settings may need a new key."), "without a provider name the line is generic")
                for (action in listOf("Configure source", "Choose another source")) {
                    val node = nodes(scene).single { text(it) == action && it.config.contains(SemanticsActions.OnClick) }
                    assertTrue(node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= 390, "$action stays on screen at ${node.boundsInRoot}")
                    assertTrue(node.boundsInRoot.height >= 44f, "$action keeps a 44 tap target, was ${node.boundsInRoot.height}")
                }
                click(scene, "Choose another source")
                assertEquals(1, clicks.choose)
            }
    }

    @Test fun theNoticeLeavesOnItsOwnAfterThirtySeconds() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(1440, 900) { PlayerScreen(null, false, null, {}, {}, {}, title, externalFacts = facts, configureSource = {}, chooseSources = {}) }
            .use { scene ->
                frames(scene)
                assertTrue(facts.tryEmit(PlayerFact.ProbableErrorSlate))
                frames(scene)
                assertTrue(texts(scene).contains("This source might not be working."))
                var time = 20 * 16_666_667L
                repeat(10) { scene.render(time).close(); time += 16_666_667L * 300 }
                assertFalse(texts(scene).contains("This source might not be working."), "the notice has had its 30 s: ${texts(scene)}")
            }
    }

    @Test fun theSlateNoticeReplacesTheAudioNoticeWhileItIsUp() {
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        scene(1440, 900) { PlayerScreen(null, false, null, {}, {}, {}, title, externalFacts = facts,
            audioNotice = "This source has no English audio.", nextSource = {}, configureSource = {}, chooseSources = {}) }
            .use { scene ->
                frames(scene)
                assertTrue(texts(scene).contains("This source has no English audio."), "the audio notice is drawn first")
                assertTrue(facts.tryEmit(PlayerFact.ProbableErrorSlate))
                frames(scene)
                assertFalse(texts(scene).contains("This source has no English audio."), "one notice at a time: ${texts(scene)}")
                assertTrue(texts(scene).contains("This source might not be working."))
            }
    }

    @Test fun televisionDrawsTheNoticeAndUpFromTheTimelineReachesIt() {
        val clicks = Clicks()
        val facts = MutableSharedFlow<PlayerFact>(extraBufferCapacity = 4)
        var focusManager: FocusManager? = null
        scene(1920, 1080, television = true) {
            PlayerScreen(null, false, null, {}, {}, {}, title, television = true, externalFacts = facts,
                tvExtras = TvPlaybackExtras(), slateProvider = "ElfHosted",
                configureSource = { clicks.configure++ }, chooseSources = { clicks.choose++ })
            focusManager = LocalFocusManager.current
        }.use { scene ->
            var frame = 0L
            fun render(count: Int = 12) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
            render(30)
            assertTrue(facts.tryEmit(PlayerFact.ProbableErrorSlate))
            render(30)
            assertTrue(texts(scene).contains("This source might not be working."), "the TV notice appears: ${texts(scene)}")
            assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsProperties.Focused) == true && text(it) == "Choose another source" },
                "the notice never takes focus by itself")
            scene.sendKeyEvent(KeyEvent(Key.DirectionUp, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.DirectionUp, KeyEventType.KeyUp))
            if (!nodes(scene).any { text(it) == "Configure source" && it.config.getOrNull(SemanticsProperties.Focused) == true })
                focusManager?.moveFocus(FocusDirection.Up)
            render(4)
            val focused = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Focused) == true }.joinToString { text(it) }
            assertEquals("Configure source", focused, "Up from the timeline reaches the notice's first action")
            click(scene, "Configure source")
            assertEquals(1, clicks.configure)
        }
    }

    private fun scene(width: Int, height: Int, television: Boolean = false, content: @Composable () -> Unit): ImageComposeScene {
        val body: @Composable () -> Unit = { ViptvTheme(television = television) {
            Box(Modifier.fillMaxSize().background(Palette.background)) { content() }
        } }
        return ImageComposeScene(width, height, Density(1f), coroutineContext = Dispatchers.Unconfined, content = body)
    }

    private fun frames(scene: ImageComposeScene, count: Int = 20) {
        var time = 0L
        repeat(count) { scene.render(time).close(); time += 16_666_667L }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    private fun texts(scene: ImageComposeScene) = nodes(scene).map(::text)
    private fun click(scene: ImageComposeScene, label: String) {
        val action = nodes(scene).single { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
        assertTrue(action.config[SemanticsActions.OnClick].action!!.invoke())
    }
    private inline fun <T> ImageComposeScene.use(block: (ImageComposeScene) -> T): T = try { block(this) } finally { close() }
}