package org.viptv.app.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
internal fun Modifier.secondaryClickMenu(enabled: Boolean, onMenu: (IntOffset) -> Unit): Modifier {
    val current = rememberUpdatedState(onMenu)
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                current.value(IntOffset(change.position.x.roundToInt(), change.position.y.roundToInt()))
                change.consume()
            }
        }
    }
}

// Inner to the click, so the hold sees the press first and consumes the release it belongs to.
@Composable
internal fun Modifier.holdMenu(enabled: Boolean, onHold: (IntOffset) -> Unit): Modifier {
    val current = rememberUpdatedState(onHold)
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val at = down.position
            val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var up = false
                while (!up) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    up = change == null || !change.pressed || (change.position - at).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (lifted != null) return@awaitEachGesture
            current.value(IntOffset(at.x.roundToInt(), at.y.roundToInt()))
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}