@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.ListEntrance
import org.viptv.app.components.PullRefreshIndicator
import org.viptv.app.components.rememberListEntrance
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class ListItemMotionTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun entrance(hasContent: State<Boolean>, sink: (ListEntrance) -> Unit): ImageComposeScene =
        ImageComposeScene(100, 100, Density(1f)) {
            ViptvTheme(false, pointer = true) { sink(rememberListEntrance(hasContent.value)) }
        }

    private fun render(scene: ImageComposeScene, frames: Int = 2) {
        var time = 0L
        repeat(frames) { scene.render(time).close(); time += 16_666_667L; Thread.sleep(2) }
    }

    @Test fun aFirstLoadAnimatesOnceContentArrives() {
        val hasContent = mutableStateOf(false)
        var animate = false
        val scene = entrance(hasContent) { animate = it.animate }
        try {
            render(scene)
            assertFalse(animate, "an empty page does not animate")
            hasContent.value = true
            render(scene)
            assertTrue(animate, "the first batch of content animates in")
        } finally { scene.close() }
    }

    @Test fun aRestoredPageNeverAnimates() {
        val hasContent = mutableStateOf(true)
        var animate = true
        val scene = entrance(hasContent) { animate = it.animate }
        try {
            render(scene)
            assertFalse(animate, "content present from the first frame is a restored page, not a first load")
        } finally { scene.close() }
    }

    @Test fun thePullIndicatorHoldsAStillArcUnderReduceMotion() {
        val reduced = ImageComposeScene(240, 240, Density(1f)) {
            ViptvTheme(false, pointer = true) {
                Box(Modifier.fillMaxSize()) {
                    PullRefreshIndicator(rememberPullToRefreshState(), refreshing = true, reduced = true, Modifier)
                }
            }
        }
        val spinning = ImageComposeScene(240, 240, Density(1f)) {
            ViptvTheme(false, pointer = true) {
                Box(Modifier.fillMaxSize()) {
                    PullRefreshIndicator(rememberPullToRefreshState(), refreshing = true, reduced = false, Modifier)
                }
            }
        }
        try {
            render(reduced, 4)
            render(spinning, 4)
            assertTrue(nodes(spinning).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate },
                "the default indicator spins while a refresh runs")
            assertFalse(nodes(reduced).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate },
                "reduce motion replaces the spinner with a still arc")
            assertTrue(nodes(reduced).any { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..1f },
                "the still arc reports its determinate progress")
        } finally {
            reduced.close(); spinning.close()
        }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> =
        scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
}
