package org.viptv.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
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
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    @Test fun browserPairingFieldHasAnInteractiveNativeInputArea() {
        var size = IntSize.Zero
        var measured = false
        val host = object : DomTextHost {
            @Composable override fun Field(spec: DomTextSpec, modifier: Modifier,
                decoration: @Composable (input: @Composable (Modifier) -> Unit) -> Unit) {
                Box(modifier.focusable().onSizeChanged { size = it; measured = true }) {
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
            scene.renderUntil(what = "browser input measurement") { measured }
            assertTrue(size.width >= 200 && size.height >= 39,
                "Browser device-code input must cover its visible slots, got $size")
        } finally { scene.close() }
    }
    @Test fun allEightDigitsFitInsideTheCardContent() {
        for ((width, compact) in listOf(440 to false, 294 to true)) {
            val scene = ImageComposeScene(width, 200, Density(1f)) {
                ViptvTheme { Box(Modifier.width(width.dp)) {
                    DeviceCodeInput("88888888", {}, {}, compact = compact, autofocus = false)
                } }
            }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            fun digits() = scene.semanticsOwners.flatMap { flatten(it.unmergedRootSemanticsNode) }.filter { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == "8"
            }
            try {
                scene.renderUntil(what = "eight code digits") { digits().size == 8 }
                assertTrue(digits().all { it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width },
                    "All code digits must fit within $width pixels")
                val widths = digits().map { it.boundsInRoot.width }
                assertTrue(widths.all { it == widths.first() && it > 0 }, "Every digit must remain fully visible: $widths")
            } finally { scene.close() }
        }
    }

}
