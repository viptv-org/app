package org.viptv.app.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.theme.Palette

private val PhoneSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

// One bottom sheet for phone layouts: scrim, handle on touch, title and a Close button,
// with drag-down-to-dismiss and the list scrolling first. Back, Escape, the scrim and the
// Close button all run the same guarded dismissal, so none of them fires twice.
@Composable
internal fun PhoneSheet(
    visible: Boolean,
    title: String,
    dismiss: () -> Unit,
    touch: Boolean,
    closeEnabled: Boolean = true,
    closeLabel: String = "Close",
    dragEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val leave: FiniteAnimationSpec<Float> = motionSpec(tween(Motion.leave, easing = Motion.exit))
    val motion = rememberOverlayMotion(visible,
        containerEnter = motionSpec(tween(Motion.panel, easing = Motion.standard)),
        scrimEnter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = leave)
    if (!motion.presented) return
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            var sheetHeight by remember { mutableFloatStateOf(0f) }
            val drag = rememberSheetDrag(enabled = touch && dragEnabled, threshold = sheetHeight / 3f, dismiss = dismiss,
                dismissSpec = leave, settleSpec = motionSpec(tween(Motion.response, easing = Motion.standard)))
            Box(Modifier.matchParentSize().graphicsLayer { alpha = motion.scrim }.background(Color.Black.copy(alpha = .62f))
                .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * .8f)
                .graphicsLayer { translationY = size.height * (1f - motion.container) + drag.offset }
                .onSizeChanged { sheetHeight = it.height.toFloat() }
                .nestedScroll(drag.nestedScroll)
                .shadow(24.dp, PhoneSheetShape).clip(PhoneSheetShape).background(Palette.surface)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { dismiss(); true }
                    else false
                }
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
                Column(Modifier.fillMaxWidth().then(drag.handle)) {
                    SheetHandle(touch, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))
                            .pointerClickable(enabled = closeEnabled, onClick = dismiss, shape = CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Lucide.X, closeLabel, Modifier.size(16.dp))
                        }
                    }
                }
                content()
            }
        }
    }
}