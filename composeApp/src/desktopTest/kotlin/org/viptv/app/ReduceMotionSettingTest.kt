package org.viptv.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.components.motionSpec
import org.viptv.app.features.settings.*
import org.junit.Rule
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class ReduceMotionSettingTest {
    @get:Rule val uiThread = UiThreadRule()

    private class MemoryStore(var raw: String? = null) : DeviceRecordStore {
        override fun read() = raw
        override fun write(value: String) { raw = value }
    }

    @Test fun onOffAndFollowPickTheChosenSource() {
        val follow = TvDisplayPreferences()
        assertEquals("Follow TV", follow.reduceMotion, "Follow TV is the default")
        assertTrue(reducedMotion(follow, system = true), "Follow TV takes the platform setting")
        assertFalse(reducedMotion(follow, system = false))
        assertTrue(reducedMotion(follow.copy(reduceMotion = "On"), system = false), "On wins over the platform")
        assertFalse(reducedMotion(follow.copy(reduceMotion = "Off"), system = true), "Off wins over the platform")
    }

    @Test fun reducedMotionSnapsDecorativeTimingButLeavesItRunning() {
        val target = mutableStateOf(0f)
        val reduced = probe(reduced = true, target = target)
        val moving = probe(reduced = false, target = target)
        try {
            render(reduced); render(moving)
            target.value = 1f
            render(reduced); render(moving)
            assertTrue(value(reduced) > .99f, "Reduced motion lands the target in one frame, saw ${value(reduced)}")
            assertTrue(value(moving) < .5f, "Without it the same transition is still running, saw ${value(moving)}")
        } finally { reduced.close(); moving.close() }
    }

    @Test fun theChosenSettingReachesEveryReaderWithoutARestart() {
        val store = MemoryStore(encodeTvDisplay(TvDisplayPreferences()))
        val display = TvDisplayState(decodeTvDisplay(store.raw), store)
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            CompositionLocalProvider(LocalTvDisplayState provides display) { ReducedMotionProbe() }
        }
        try {
            render(scene)
            assertEquals("false", description(scene), "Follow TV on desktop means motion")
            display.change(TvDisplayPreferences(reduceMotion = "On"))
            render(scene)
            assertEquals("true", description(scene), "On reaches the readers without a restart")
            display.change(TvDisplayPreferences(reduceMotion = "Off"))
            render(scene)
            assertEquals("false", description(scene))
        } finally { scene.close() }
    }

    @Composable
    private fun ReducedMotionProbe() {
        val reduced = rememberReducedMotion()
        Box(Modifier.size(10.dp).semantics { stateDescription = reduced.toString() })
    }

    @Composable
    private fun MotionTargetProbe(target: Float) {
        val value by animateFloatAsState(target, motionSpec(tween(500)))
        Box(Modifier.size(10.dp).semantics { stateDescription = value.toString() })
    }

    private fun probe(reduced: Boolean, target: State<Float>): ImageComposeScene =
        ImageComposeScene(100, 100, Density(1f)) {
            CompositionLocalProvider(LocalReducedMotion provides reduced) { MotionTargetProbe(target.value) }
        }

    private fun render(scene: ImageComposeScene, frames: Int = 3) {
        var time = clocks[scene] ?: 0L
        repeat(frames) { time += 16_666_667L; scene.render(time).close() }
        clocks[scene] = time
    }
    private val clocks = mutableMapOf<ImageComposeScene, Long>()

    private fun value(scene: ImageComposeScene) = description(scene).toFloat()

    private fun description(scene: ImageComposeScene): String =
        nodes(scene).first { it.config.contains(SemanticsProperties.StateDescription) }
            .config[SemanticsProperties.StateDescription]

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
