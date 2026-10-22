package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.viptv.app.features.sources.TvSourcesFilters
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * The outside focus ring must follow the control's own shape and must not be
 * clipped by the scroll container it sits in (source selector pills on a 1080p TV).
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TvFocusRingShapeTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    private fun luminance(argb: Int) = ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3

    @Test fun sourcePillRingWrapsThePillAndIsNotClipped() = onEdt {
        // A 1080p Android TV at 320 dpi: 960 dp wide, so the board scale is 0.5.
        val scale = .5f
        val quality = FocusRequester()
        val scene = ImageComposeScene(800, 300, Density(2f)) { ViptvTheme(true) {
            Column(Modifier.fillMaxSize().background(Palette.background).padding(start = 40.dp, top = 40.dp)) {
                TvSourcesFilters(emptyList(), emptyMap(), scale, quality = null, provider = null, qualityChanged = {},
                    providerChanged = {}, qualityFocus = quality, providerFocus = FocusRequester(), enterRows = {})
            }
        } }
        try {
            scene.render(0)
            quality.requestFocus()
            scene.render(16_000_000)
            val image = scene.render(1_000_000_000)
            val bitmap = Bitmap.makeFromImage(image)
            File("build/test-results").mkdirs()
            File("build/test-results/tv-source-pill-ring.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val pill = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(true) }.first { node ->
                node.config.contains(SemanticsActions.OnClick) &&
                    node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }?.startsWith("All 0") == true
            }.boundsInRoot
            val left = pill.left.toInt(); val top = pill.top.toInt(); val middle = pill.center.y.toInt()
            assertEquals(80, left, "the ring room must not move the first pill")
            val ring = (4 * scale * 2).toInt() // 4 board px at scale, in device px
            assertTrue(luminance(bitmap.getColor(left - ring / 2, middle)) > 200, "ring left of the first pill is clipped")
            assertTrue(luminance(bitmap.getColor(left - ring - 3, middle)) < 40, "ring reaches too far out")
            // The pill is a capsule; a ring with tighter corners paints its top-left box corner.
            assertTrue(luminance(bitmap.getColor(left + 2, top + 2)) < 40, "ring corners do not follow the pill")
        } finally { scene.close() }
    }
}
