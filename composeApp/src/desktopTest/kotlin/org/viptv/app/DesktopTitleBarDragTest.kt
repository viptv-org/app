package org.viptv.app

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import org.junit.Rule
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class DesktopTitleBarDragTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun unusedTitleBarPressReachesTheWindowDragHandler() {
        var dragPresses = 0
        val scene = ImageComposeScene(800, 200) {
            ViptvTheme(pointer = true) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        dragPresses++
                    }
                }) {
                    DesktopTitleBar(ApplicationState(screen = Screen.Connect), false,
                        {}, {}, {}, {}, {}, {}, Modifier)
                }
            }
        }
        try {
            scene.render(0).close()
            scene.sendPointerEvent(PointerEventType.Press, Offset(300f, 20f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(300f, 20f))
            assertEquals(1, dragPresses, "The draggable parent must receive an unconsumed title-bar press")
        } finally { scene.close() }
    }
}
