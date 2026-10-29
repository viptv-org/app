package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.viptv.app.components.LocalTvScale
import org.viptv.app.features.sources.SourceFilters
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.io.File
import org.junit.Rule
import kotlin.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvFocusRingShapeTest {
    @get:Rule val uiThread = UiThreadRule()
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private fun luminance(argb: Int) = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3

    @Test fun tvFilterChipRingWrapsThePillAndIsNotClipped() = onEdt {
        val scale = .5f
        val scene = ImageComposeScene(800, 300, Density(2f)) { ViptvTheme(true) {
            Column(Modifier.fillMaxSize().background(Palette.background).padding(start = 40.dp, top = 40.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalTvScale provides scale) {
                    SourceFilters(emptyList(), null, null, {}, {}, gutter = 0.dp)
                }
            }
        } }
        try {
            scene.render(0)
            val chip = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.first { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList &&
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.startsWith("Quality: All") == true
            }
            assertTrue(chip.config[SemanticsActions.RequestFocus].action!!.invoke(), "the TV chip is a focus stop")
            scene.render(16_000_000)
            val image = scene.render(1_000_000_000)
            val bitmap = Bitmap.makeFromImage(image)
            File("build/test-results").mkdirs()
            File("build/test-results/tv-filter-chip-ring.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val pill = chip.boundsInRoot
            val left = pill.left.toInt(); val top = pill.top.toInt(); val middle = pill.center.y.toInt()
            assertEquals(80, left, "the ring room must not move the first chip")
            val ring = (4 * scale * 2).toInt()
            assertTrue(luminance(bitmap.getColor(left - ring / 2, middle)) > 200, "ring left of the first chip is clipped")
            assertTrue(luminance(bitmap.getColor(left - ring - 3, middle)) < 40, "ring reaches too far out")
            // The chip is a capsule; a ring with tighter corners paints its top-left box corner.
            assertTrue(luminance(bitmap.getColor(left + 2, top + 2)) < 40, "ring corners do not follow the pill")
        } finally { scene.close() }
    }
}