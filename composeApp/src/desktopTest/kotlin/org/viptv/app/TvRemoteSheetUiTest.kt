package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.viptv.app.features.remote.RemoteHeaderButton
import org.viptv.app.features.remote.TvRemoteOverlay
import org.viptv.app.features.remote.VizioRemoteEngine
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailure
import org.viptv.engine.wire.VizioFailureKind
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalComposeUiApi::class)
class TvRemoteSheetUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private val tv = VizioTv("Living Room", "https://192.168.1.20:7345")

    private class FakeEngine(vararg outputs: VizioControllerOutput) : VizioRemoteEngine {
        val operations = mutableListOf<String>()
        private val queue = ArrayDeque(outputs.toList())
        override suspend fun run(operation: String, input: JsonObject): VizioControllerOutput {
            operations += operation
            return if (queue.isEmpty()) complete() else queue.removeFirst()
        }
        override fun close() = Unit
    }

    @Test fun theSheetShowsEveryControlAndSendsKeysOnceOnline() {
        val engine = FakeEngine()
        val scene = scene(390, 844) { scope ->
            TvRemoteOverlay(tv, "https://example.invalid/tv/?platform=vizio", false, {}, scope, {}) { engine }
        }
        val probe = Probe(scene)
        try {
            probe.await("the connected status") { probe.has("Connected · 192.168.1.20:7345") }
            assertTrue(probe.has("Living Room"), probe.texts().toString())
            assertTrue(probe.has("Open VIPTV on TV"), probe.texts().toString())
            assertTrue(probe.has("Buttons"), probe.texts().toString())
            assertTrue(probe.has("Swipe"), probe.texts().toString())
            assertTrue(probe.has("Back"), probe.texts().toString())
            assertTrue(probe.has("Play"), probe.texts().toString())
            assertTrue(probe.has("Pause"), probe.texts().toString())
            assertTrue(probe.has("Volume"), probe.texts().toString())
            probe.click("OK")
            probe.await("the OK key") { engine.operations.any { it == "key" } }
        } finally { scene.close() }
    }

    @Test fun anUnreachableTvShowsTheOfflineCardInsteadOfTheLaunchButton() {
        val engine = FakeEngine(error(VizioFailureKind.TRANSPORT))
        val scene = scene(390, 844) { scope ->
            TvRemoteOverlay(tv, "https://example.invalid/tv/?platform=vizio", false, {}, scope, {}) { engine }
        }
        val probe = Probe(scene)
        try {
            probe.await("the offline card") { probe.has("Can't reach Living Room") }
            assertTrue(probe.has("Not reachable"), probe.texts().toString())
            assertTrue(probe.has("Turn the TV on and check it's on the same Wi-Fi as this phone."), probe.texts().toString())
            assertTrue(probe.has("Try again"), probe.texts().toString())
            assertTrue(probe.has("Pair again"), probe.texts().toString())
            assertFalse(probe.has("Open VIPTV on TV"), probe.texts().toString())
        } finally { scene.close() }
    }

    @Test fun pairAgainFromTheOfflineCardInvokesTheRecovery() {
        val engine = FakeEngine(error(VizioFailureKind.TRANSPORT))
        var paired = 0
        val scene = scene(390, 844) { scope ->
            TvRemoteOverlay(tv, "https://example.invalid/tv/?platform=vizio", false, {}, scope, {}, onPairAgain = { paired++ }) { engine }
        }
        val probe = Probe(scene)
        try {
            probe.await("the offline card") { probe.has("Can't reach Living Room") }
            probe.click("Pair again")
            assertEquals(1, paired)
        } finally { scene.close() }
    }

    @Test fun swipeModeReplacesTheDpadWithTheTouchpad() {
        val engine = FakeEngine()
        val swipe = mutableStateOf(false)
        val scene = scene(390, 844) { scope ->
            TvRemoteOverlay(tv, "https://example.invalid/tv/?platform=vizio", swipe.value, { swipe.value = it }, scope, {}) { engine }
        }
        val probe = Probe(scene)
        try {
            probe.await("the connected status") { probe.has("Connected · 192.168.1.20:7345") }
            probe.click("Swipe")
            probe.await("the touchpad") { probe.has("Swipe to move · Tap for OK") }
        } finally { scene.close() }
    }

    @Test fun theHeaderTipPointsAtTheRemoteAndDismisses() {
        var dismissed = 0
        var opened = 0
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                RemoteHeaderButton("Living Room", showTip = true, dismissTip = { dismissed++ }, open = { opened++ })
            } }
        }
        val probe = Probe(scene)
        try {
            probe.frames(8)
            assertTrue(probe.has("Your TV remote"), probe.texts().toString())
            assertTrue(probe.has("It stays up here on Discover, Live and My List. Turn it off in Settings › Watch on TV."), probe.texts().toString())
            probe.click("Got it")
            assertEquals(1, dismissed)
            probe.clickContent("TV remote: Living Room")
            assertEquals(1, opened)
        } finally { scene.close() }
    }

    private fun scene(width: Int, height: Int, content: @Composable (kotlinx.coroutines.CoroutineScope) -> Unit): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                content(scope)
            } }
        }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun contents() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        fun has(value: String) = value in texts()
        private fun action(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        private fun contentAction(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value) == true
        }
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\" in ${texts()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), "Could not click \"$value\"")
            frames()
        }
        fun clickContent(value: String) {
            val node = assertNotNull(contentAction(value), "No clickable content \"$value\" in ${contents()}")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), "Could not click content \"$value\"")
            frames()
        }
        fun await(what: String, condition: () -> Boolean) {
            frame = scene.renderUntil(timeout = 10.seconds, from = frame, what = what, condition = condition)
            frames()
        }
    }
}

private fun complete() = VizioControllerOutput(VizioControllerOutputKind.COMPLETE, null, null, null, null, false)

private fun error(kind: VizioFailureKind) = VizioControllerOutput(
    VizioControllerOutputKind.ERROR, null, null, null,
    VizioFailure(kind, "failed", false, null), false,
)