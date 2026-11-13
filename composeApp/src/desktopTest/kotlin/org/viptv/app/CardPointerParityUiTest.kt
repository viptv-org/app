package org.viptv.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.viptv.app.components.TextButton
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.features.home.MediaCard
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)
class CardPointerParityUiTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()
    private val card = Card(MediaIdentity("fixture", "movie", emptyList()), provider_id = "fixture", title = "Fixture movie", badges = emptyList())

    @Test fun hoverAndDialogDismissKeepTheCardWithinItsOwnLayout() {
        for (width in listOf(420, 1000)) {
            var dialog by mutableStateOf(false)
            val actions = CardActions({}, {}, { false })
            val scene = ImageComposeScene(width, 600, Density(1f)) { ViptvTheme(pointer = true) {
                CompositionLocalProvider(LocalCardActions provides actions) {
                    Box(Modifier.fillMaxSize().padding(20.dp)) { MediaCard(card, width = 180, portrait = true, open = { dialog = true }) }
                    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Details") },
                        confirmButton = { TextButton({ dialog = false }) { Text("Close") } })
                }
            } }
            fun nodes() = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(false) }
            fun hasPreview() = nodes().any { "More info" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            var frame = 0L
            fun hoverInterval() { repeat(60) { scene.render(frame++ * 16_666_667L).close() } }
            try {
                scene.renderUntil(what = "card title") { nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Fixture movie" } } }
                scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 100f))
                hoverInterval()
                System.getenv("VIPTV_CARD_PARITY_CAPTURE")?.let { path ->
                    File(path).mkdirs()
                    scene.render(frame++ * 16_666_667L).use { picture -> picture.encodeToData()!!.use {
                        File(path, "hover-$width.png").writeBytes(it.bytes)
                    } }
                }
                assertFalse(hasPreview(), "Pointer hover must not add a floating preview at width $width")
                val resizedWidth = if (width == 420) 1000 else 420
                scene.constraints = Constraints.fixed(resizedWidth, 600)
                hoverInterval()
                assertFalse(hasPreview(), "Resize must not introduce or reposition an overlay preview")
                scene.constraints = Constraints.fixed(width, 600)
                hoverInterval()
                dialog = true
                scene.renderUntil(what = "details dialog") { nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Close" } } }
                dialog = false
                hoverInterval()
                assertFalse(hasPreview(), "Closing details must not reveal a stale preview")
            } finally { scene.close() }
        }
    }
}
