package org.viptv.app.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal class SheetDrag(val offset: Float, val handle: Modifier, val nestedScroll: NestedScrollConnection)

private const val SheetFlingVelocity = 1100f

// The offset animates back to 0 with the leave, so a dismissal never jumps open first.
@Composable
internal fun rememberSheetDrag(
    enabled: Boolean,
    threshold: Float,
    dismiss: () -> Unit,
    dismissSpec: FiniteAnimationSpec<Float>,
    settleSpec: FiniteAnimationSpec<Float>,
): SheetDrag {
    val offset = remember { mutableFloatStateOf(0f) }
    val dismissed = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentDismiss = rememberUpdatedState(dismiss)
    val currentThreshold = rememberUpdatedState(threshold)

    fun release(velocity: Float) {
        if (!enabled || dismissed.value) return
        if (offset.floatValue > currentThreshold.value || velocity > SheetFlingVelocity) {
            dismissed.value = true
            scope.launch { animate(offset.floatValue, 0f, animationSpec = dismissSpec) { value, _ -> offset.floatValue = value } }
            currentDismiss.value()
        } else {
            scope.launch { animate(offset.floatValue, 0f, animationSpec = settleSpec) { value, _ -> offset.floatValue = value } }
        }
    }

    val nestedScroll = remember(enabled) {
        object : NestedScrollConnection {
            private fun dragScroll(source: NestedScrollSource) = source == NestedScrollSource.Drag || source == NestedScrollSource.UserInput

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || !dragScroll(source) || offset.floatValue <= 0f || available.y >= 0f) return Offset.Zero
                val before = offset.floatValue
                offset.floatValue = (before + available.y).coerceAtLeast(0f)
                return Offset(0f, offset.floatValue - before)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || !dragScroll(source) || available.y <= 0f) return Offset.Zero
                val before = offset.floatValue
                offset.floatValue = (before + available.y).coerceAtLeast(0f)
                return Offset(0f, offset.floatValue - before)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!enabled || offset.floatValue <= 0f) return Velocity.Zero
                release(available.y)
                return Velocity.Zero
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceAtLeast(0f)
    }
    val handle = Modifier.draggable(
        orientation = Orientation.Vertical,
        enabled = enabled,
        state = draggableState,
        onDragStopped = { velocity -> release(velocity) },
    )
    return SheetDrag(offset.floatValue, handle, nestedScroll)
}

@Composable
internal fun SheetHandle(touch: Boolean, modifier: Modifier = Modifier) {
    if (!touch) return
    Box(modifier.size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .32f)))
}