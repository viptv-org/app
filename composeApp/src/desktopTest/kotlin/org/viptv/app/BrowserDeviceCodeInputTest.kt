package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.junit.Rule
import org.viptv.app.components.DomTextHost
import org.viptv.app.components.DomTextSpec
import org.viptv.app.components.LocalDomTextHost
import org.viptv.app.features.entry.DeviceCodeInput
import org.viptv.app.theme.ViptvTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class BrowserDeviceCodeInputTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun browserPairingFieldHasAnInteractiveNativeInputArea() {
        var size = IntSize.Zero
        val host = object : DomTextHost {
            @Composable override fun Field(spec: DomTextSpec, modifier: Modifier,
                decoration: @Composable (input: @Composable (Modifier) -> Unit) -> Unit) {
                Box(modifier.focusable().onSizeChanged { size = it }) {
                    decoration { inputModifier -> Box(inputModifier) }
                }
            }
        }
        val scene = ImageComposeScene(800, 600, Density(1f)) {
            CompositionLocalProvider(LocalDomTextHost provides host) {
                ViptvTheme { DeviceCodeInput("", {}, {}, autofocus = false) }
            }
        }
        try {
            repeat(3) { scene.render(it * 16_666_667L).close() }
            assertTrue(size.width >= 200 && size.height >= 39,
                "Browser device-code input must cover its visible slots, got $size")
        } finally { scene.close() }
    }
}
