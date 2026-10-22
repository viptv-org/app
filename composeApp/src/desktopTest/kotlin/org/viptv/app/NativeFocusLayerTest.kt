package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image
import org.viptv.app.components.OwnFocusTreatment
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/** Controls that draw the board's ring must not also get Material's focus layer (doubled TV focus ring). */
@OptIn(ExperimentalComposeUiApi::class)
class NativeFocusLayerTest {
    private val edt = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = !EventQueue.isDispatchThread()
        override fun dispatch(context: CoroutineContext, block: Runnable) = EventQueue.invokeLater(block)
    }
    private fun onEdt(block: suspend CoroutineScope.() -> Unit) = runBlocking(edt, block)

    /** The control's centre pixel once it holds focus; the page is [Palette.background]. */
    private fun focusedCentre(control: @Composable (Modifier) -> Unit): Int {
        var pixel = 0
        onEdt {
            val focus = FocusRequester()
            val scene = ImageComposeScene(200, 200, Density(1f)) { ViptvTheme(true) {
                Box(Modifier.fillMaxSize().background(Palette.background), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    control(Modifier.size(100.dp).focusRequester(focus))
                }
            } }
            try {
                scene.render(0)
                focus.requestFocus()
                scene.render(16_000_000)
                val image: Image = scene.render(1_000_000_000)
                pixel = org.jetbrains.skia.Bitmap.makeFromImage(image).getColor(100, 100)
            } finally { scene.close() }
        }
        return pixel
    }

    private val page = Palette.background.let { c ->
        (0xFF shl 24) or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt()
    }

    @Test fun materialPaintsAFocusLayerOnDefaultControls() {
        assertNotEquals(page, focusedCentre { IconButton({}, it) { } }, "IconButton focus layer")
        assertNotEquals(page, focusedCentre { Box(it.clickable { }) }, "default clickable focus layer")
    }

    @Test fun ownFocusTreatmentRemovesTheNativeLayer() {
        assertEquals(page, focusedCentre { OwnFocusTreatment { IconButton({}, it) { } } }, "IconButton focus layer")
        assertEquals(page, focusedCentre { OwnFocusTreatment { Box(it.clickable { }) } }, "default clickable focus layer")
    }
}
