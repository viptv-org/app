package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.viptv.app.features.settings.VizioPairResult
import org.viptv.app.features.settings.VizioSetupEngine
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.features.settings.WatchOnTvSetupFlow
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.VizioPairingChallenge
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WatchOnTvSetupUiTest {
    @get:Rule val uiThread = UiThreadRule()

    private class FakeEngine : VizioSetupEngine {
        override suspend fun search(limit: Int, onResult: (VizioTv) -> Unit) {
            onResult(VizioTv("Living Room", "https://192.168.1.20:7345"))
        }
        override suspend fun probe(origin: String): VizioTv? = null
        override suspend fun beginPair(tv: VizioTv): VizioPairingChallenge? = VizioPairingChallenge(1, 42L)
        override suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult =
            VizioPairResult.Paired
        override suspend fun cancelPair(tv: VizioTv) = Unit
    }

    @Test fun introLeadsThroughSearchToThePinStep() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                WatchOnTvSetupFlow(FakeEngine(), compact = true, back = {}, onPaired = {})
            } }
        }
        val probe = Probe(scene)
        try {
            probe.frames(12)
            assertTrue(probe.has("Use this phone as your TV remote"), probe.texts().toString())
            assertTrue(probe.has("VIPTV searches your local network only to find your TV."), probe.texts().toString())
            probe.click("Set up a TV")
            probe.await("the discovered TV") { probe.has("Living Room") }
            probe.click("Living Room")
            probe.await("the PIN step") { probe.has("New PIN") }
            assertTrue(probe.has("Enter the PIN on your TV"), probe.texts().toString())
            assertTrue(probe.has("Pairs as soon as all 4 digits are in."), probe.texts().toString())
        } finally { scene.close() }
    }

    @Test fun manualEntryIsReachableFromTheSearchStep() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                WatchOnTvSetupFlow(FakeEngine(), compact = true, back = {}, onPaired = {})
            } }
        }
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Set up a TV")
            probe.await("the search footer") { probe.has("Enter IP address") }
            probe.click("Enter IP address")
            probe.await("the manual step") { probe.has("Enter your TV's IP address") }
            assertTrue(probe.has("Find it on the TV under Settings › Network."), probe.texts().toString())
        } finally { scene.close() }
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close(); Thread.sleep(4) }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun has(value: String) = value in texts()
        private fun action(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\"")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), "Could not click \"$value\"")
            frames()
        }
        fun await(what: String, condition: () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!condition()) {
                assertTrue(System.nanoTime() < deadline, "Timed out waiting for $what: ${texts()}")
                frames(6); Thread.sleep(50)
            }
            frames()
        }
    }
}