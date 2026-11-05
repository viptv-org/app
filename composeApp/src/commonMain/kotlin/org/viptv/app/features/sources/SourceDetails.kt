package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.AccessibleIconButton
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.requestFocusWhenPlaced
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

internal const val SOURCE_DETAILS_LABEL = "Source details"

@Composable
internal fun SourceDetailsPanel(source: SourceCandidate, television: Boolean, scale: Float, dismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    val inset = if (television) 48.dp * scale else 24.dp
    Box(Modifier.fillMaxSize(), contentAlignment = if (television) Alignment.CenterEnd else Alignment.Center) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (television) .6f else .62f))
            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
        Column(Modifier.fillMaxWidth().padding(if (television) 0.dp else 24.dp)
            .widthIn(max = if (television) 820.dp * scale else 560.dp)
            .clip(RoundedCornerShape(if (television) 0.dp else 18.dp)).background(Palette.raised)
            .semantics { paneTitle = SOURCE_DETAILS_LABEL }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { dismiss(); true }
                else false
            }.padding(inset)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(SOURCE_DETAILS_LABEL, Modifier.weight(1f).semantics { heading() },
                    fontSize = if (television) 36.sp * scale else 18.sp, fontWeight = FontWeight.Bold)
                AccessibleIconButton(Lucide.X, "Close", dismiss, iconSize = if (television) 24.dp * scale else 18.dp, wash = null)
            }
            Column(Modifier.heightIn(max = if (television) 420.dp * scale else 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (television) 16.dp * scale else 10.dp)) {
                Text(source.label, fontSize = if (television) 28.sp * scale else 15.sp, fontWeight = FontWeight.SemiBold)
                source.descriptionText()?.let { Text(it, fontSize = if (television) 24.sp * scale else 13.sp,
                    lineHeight = if (television) 34.sp * scale else 19.sp, color = Palette.secondary) }
            }
            TextButton(onClick = dismiss, modifier = Modifier.padding(top = if (television) 24.dp * scale else 16.dp)
                .heightIn(min = if (television) 72.dp * scale else 44.dp).then(close.requestFocusWhenPlaced())) {
                Text("Close", fontSize = if (television) 24.sp * scale else 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
