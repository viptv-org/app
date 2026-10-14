package org.viptv.app.components

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first

/** Requests once per loaded page when the viewport approaches its end; failures never spin. */
@Composable
internal fun AutomaticPage(count: Int, busy: Boolean, more: (() -> Unit)?, lastVisible: () -> Int) {
    val next by rememberUpdatedState(more)
    val loading by rememberUpdatedState(busy)
    val viewport by rememberUpdatedState(lastVisible)
    LaunchedEffect(count, more != null) {
        if (count == 0 || more == null) return@LaunchedEffect
        snapshotFlow { !loading && viewport() >= (count - 12).coerceAtLeast(0) }.first { it }
        next?.invoke()
    }
}
