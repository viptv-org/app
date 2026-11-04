package org.viptv.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.viptv.app.components.LIST_END_COPY
import org.viptv.app.components.LIST_LOAD_MORE_COPY
import org.viptv.app.components.LIST_RETRY_COPY
import org.viptv.app.components.ListEndFooter
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ListEndFooterUiTest {
    @get:Rule val uiThread = UiThreadRule()

    @Composable
    private fun FooterView(modifier: Modifier = Modifier, loading: Boolean = false, ended: Boolean = false,
        failed: Boolean = false, television: Boolean = false, scale: Float = 1f) {
        ViptvTheme(television) { Surface(Modifier.fillMaxSize(), color = Palette.background) {
            ListEndFooter(loading = loading, ended = ended, failed = failed, retry = {}, modifier = modifier,
                television = television, scale = scale)
        } }
    }

    @Test fun errorTileFitsAtPhoneWidth() {
        val scene = ImageComposeScene(390, 844, Density(1f)) {
            FooterView(Modifier.fillMaxWidth().padding(horizontal = 24.dp), failed = true)
        }
        try {
            frames(scene)
            assertTextFits(scene, LIST_LOAD_MORE_COPY, 390)
            assertTextFits(scene, LIST_RETRY_COPY, 390)
        } finally { scene.close() }
    }

    @Test fun loadingAndEndFitAtPhoneWidth() {
        val loading = ImageComposeScene(390, 844, Density(1f)) {
            FooterView(Modifier.fillMaxWidth().padding(horizontal = 24.dp), loading = true)
        }
        try {
            frames(loading)
            assertTextFits(loading, "Loading more titles…", 390)
        } finally { loading.close() }

        val ended = ImageComposeScene(390, 844, Density(1f)) {
            FooterView(Modifier.fillMaxWidth().padding(horizontal = 24.dp), ended = true)
        }
        try {
            frames(ended)
            assertTextFits(ended, LIST_END_COPY, 390)
        } finally { ended.close() }
    }

    @Test fun statesFitOnTv() {
        val failed = ImageComposeScene(1920, 1080, Density(1f)) {
            FooterView(Modifier.fillMaxWidth().padding(horizontal = 96.dp), failed = true, television = true, scale = 1f)
        }
        try {
            frames(failed)
            assertTextFits(failed, LIST_LOAD_MORE_COPY, 1920)
            assertTextFits(failed, LIST_RETRY_COPY, 1920)
        } finally { failed.close() }

        val ended = ImageComposeScene(1920, 1080, Density(1f)) {
            FooterView(Modifier.fillMaxWidth().padding(horizontal = 96.dp), ended = true, television = true, scale = 1f)
        }
        try {
            frames(ended)
            assertTextFits(ended, LIST_END_COPY, 1920)
        } finally { ended.close() }
    }

    private fun assertTextFits(scene: ImageComposeScene, expected: String, viewport: Int) {
        val node = unmerged(scene).single { text(it) == expected }
        assertTrue(node.boundsInRoot.left >= -1f && node.boundsInRoot.right <= viewport + 1f,
            "'$expected' runs off the viewport (${node.boundsInRoot})")
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        val layout = results.single()
        for (line in 0 until layout.lineCount) {
            val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue(lineWidth <= node.boundsInRoot.width + 0.5f,
                "'$expected' line $line is cut horizontally (${lineWidth}px in a ${node.boundsInRoot.width}px box)")
        }
        assertTrue(node.boundsInRoot.height + 0.5f >= layout.getLineBottom(layout.lineCount - 1) - layout.getLineTop(0),
            "'$expected' is cut vertically")
    }

    private fun frames(scene: ImageComposeScene) = repeat(20) { scene.render((it + 1) * 16_666_667L).close() }
    private fun unmerged(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
}