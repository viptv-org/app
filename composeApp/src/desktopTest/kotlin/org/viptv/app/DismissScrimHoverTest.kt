package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.dismissScrim
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class DismissScrimHoverTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun pointerMovementDoesNotRepaintTheBackdropAndClickStillDismisses() {
        for (explicitInteraction in listOf(false, true)) {
            var dismisses = 0
            val scene = ImageComposeScene(200, 200) { ViptvTheme(pointer = true) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().background(Color(0xFF282828)).then(
                    if (explicitInteraction) Modifier.dismissScrim(interaction) { dismisses++ }
                    else Modifier.dismissScrim { dismisses++ }))
            } }
            var frame = 0L
            fun pixel(): Int {
                repeat(20) { scene.render(frame++ * 16_666_667L).close() }
                return scene.render(frame++ * 16_666_667L).use { image -> Bitmap.makeFromImage(image).use { it.getColor(100, 100) } }
            }
            try {
                val resting = pixel()
                scene.sendPointerEvent(PointerEventType.Move, Offset(50f, 50f))
                assertEquals(resting, pixel(), "Hover must not brighten the whole dismiss backdrop")
                scene.sendPointerEvent(PointerEventType.Press, Offset(50f, 50f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(50f, 50f))
                assertEquals(1, dismisses)
            } finally { scene.close() }
        }
    }
}
