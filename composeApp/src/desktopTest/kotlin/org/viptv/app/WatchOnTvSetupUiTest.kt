package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CompletableDeferred
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.features.settings.VizioPairResult
import org.viptv.app.features.settings.VizioSetupEngine
import org.viptv.app.features.settings.VizioTv
import org.viptv.app.features.settings.WatchOnTvSetupFlow
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.VizioPairingChallenge
import org.junit.Rule
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalComposeUiApi::class)
class WatchOnTvSetupUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

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
            probe.await("the ready PIN step") { probe.has("Pairs as soon as all 4 digits are in.") }
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

    @Test fun searchShowsProgressWhileLookingAndClearsWhenResultsArrive() {
        val gate = CompletableDeferred<Unit>()
        val scene = scene(engine(search = { _, onResult ->
            gate.await()
            onResult(VizioTv("Living Room", "https://192.168.1.20:7345"))
        }))
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Set up a TV")
            probe.frames(4)
            assertTrue(probe.has("Looking for Vizio TVs on your Wi-Fi…"), probe.texts().toString())
            assertTrue(probe.hasProgress(), probe.texts().toString())

            gate.complete(Unit)
            probe.await("the discovered TV") { probe.has("Living Room") }
            probe.frames(4)
            assertFalse(probe.hasProgress(), probe.texts().toString())
        } finally { scene.close() }
    }

    @Test fun searchKeepsPartialResultsVisibleWithProgressWhileStillRunning() {
        val gate = CompletableDeferred<Unit>()
        val scene = scene(engine(search = { _, onResult ->
            onResult(VizioTv("Living Room", "https://192.168.1.20:7345"))
            gate.await()
        }))
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Set up a TV")
            probe.await("a result while still searching") { probe.has("Living Room") }
            assertTrue(probe.hasProgress(), "discovery still running keeps an indicator: ${probe.texts()}")

            gate.complete(Unit)
            probe.await("the settled search") { !probe.hasProgress() }
            assertFalse(probe.hasProgress(), "discovery settled clears the indicator: ${probe.texts()}")
        } finally { scene.close() }
    }

    @Test fun pairingShowsProgressWhileConnectingAndSubmitting() {
        val beginGate = CompletableDeferred<Unit>()
        val finishGate = CompletableDeferred<Unit>()
        val scene = scene(engine(
            beginPair = { beginGate.await(); VizioPairingChallenge(1, 42L) },
            finishPair = { _, _, _ -> finishGate.await(); VizioPairResult.Paired },
        ))
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Set up a TV")
            probe.await("the discovered TV") { probe.has("Living Room") }
            probe.click("Living Room")
            probe.frames(4)
            assertTrue(probe.has("Connecting to Living Room…"), probe.texts().toString())
            assertTrue(probe.hasProgress(), probe.texts().toString())

            beginGate.complete(Unit)
            probe.await("the PIN boxes") { probe.has("New PIN") }
            probe.frames(4)
            assertFalse(probe.hasProgress(), probe.texts().toString())

            probe.setText("1234")
            probe.frames(4)
            assertTrue(probe.has("Pairing…"), probe.texts().toString())
            assertTrue(probe.hasProgress(), probe.texts().toString())

            finishGate.complete(Unit)
            probe.await("the done step") { probe.has("Connected to Living Room") }
            probe.frames(4)
            assertFalse(probe.hasProgress(), probe.texts().toString())
        } finally { scene.close() }
    }

    @Test fun reducedMotionKeepsAStaticProgressIndicator() {
        val gate = CompletableDeferred<Unit>()
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
                    WatchOnTvSetupFlow(engine(search = { _, _ -> gate.await() }), compact = true, back = {}, onPaired = {})
                } }
            }
        }
        val probe = Probe(scene)
        try {
            probe.frames(12)
            probe.click("Set up a TV")
            probe.frames(4)
            assertTrue(probe.has("Looking for Vizio TVs on your Wi-Fi…"), probe.texts().toString())
            assertFalse(probe.hasProgress(), "reduced motion drops the looping indicator: ${probe.texts()}")
            assertTrue(probe.hasProgressBar(), "a static status marker remains: ${probe.texts()}")
            gate.complete(Unit)
            probe.frames(4)
        } finally { scene.close() }
    }

    private fun scene(engine: VizioSetupEngine) = ImageComposeScene(390, 844, Density(1f)) {
        ViptvTheme { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            WatchOnTvSetupFlow(engine, compact = true, back = {}, onPaired = {})
        } }
    }

    private fun engine(
        search: suspend (Int, (VizioTv) -> Unit) -> Unit = { _, onResult -> onResult(VizioTv("Living Room", "https://192.168.1.20:7345")) },
        beginPair: suspend (VizioTv) -> VizioPairingChallenge? = { VizioPairingChallenge(1, 42L) },
        finishPair: suspend (VizioTv, VizioPairingChallenge, String) -> VizioPairResult = { _, _, _ -> VizioPairResult.Paired },
    ) = object : VizioSetupEngine {
        override suspend fun search(limit: Int, onResult: (VizioTv) -> Unit) = search(limit, onResult)
        override suspend fun probe(origin: String): VizioTv? = null
        override suspend fun beginPair(tv: VizioTv): VizioPairingChallenge? = beginPair(tv)
        override suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult = finishPair(tv, challenge, pin)
        override suspend fun cancelPair(tv: VizioTv) = Unit
    }

    private class Probe(val scene: ImageComposeScene) {
        private var frame = 0L
        fun frames(count: Int = 20) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
        private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
        private fun nodes() = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        fun has(value: String) = value in texts()
        fun hasProgress() = nodes().any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate }
        fun hasProgressBar() = nodes().any { it.config.contains(SemanticsProperties.ProgressBarRangeInfo) }
        fun setText(value: String) {
            val field = nodes().single { it.config.contains(SemanticsActions.SetText) }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)), "Could not type \"$value\"")
            frames()
        }
        private fun action(value: String) = nodes().firstOrNull { node ->
            node.config.contains(SemanticsActions.OnClick) && node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun click(value: String) {
            val node = assertNotNull(action(value), "No clickable \"$value\"")
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke(), "Could not click \"$value\"")
            frames()
        }
        fun await(what: String, condition: () -> Boolean) {
            frame = scene.renderUntil(timeout = 10.seconds, from = frame, what = what, condition = condition)
            frames()
        }
    }
}