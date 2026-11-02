package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val TvHoldMillis = 700L

private val TvHoldKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

@Composable
internal fun Modifier.tvHold(enabled: Boolean = true, holdMillis: Long = TvHoldMillis, onHold: () -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val current = rememberUpdatedState(onHold)
    var down by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun reset() { down = false; held = false; job?.cancel(); job = null }
    return onFocusChanged { if (!it.isFocused) reset() }.onPreviewKeyEvent { event ->
        if (!enabled || event.key !in TvHoldKeys) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (!down) {
                    down = true
                    held = false
                    job = scope.launch {
                        delay(holdMillis)
                        if (down) { held = true; current.value() }
                    }
                }
                false
            }
            else -> {
                val suppress = down && held
                reset()
                suppress
            }
        }
    }
}