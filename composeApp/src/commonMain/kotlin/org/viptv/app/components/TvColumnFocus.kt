package org.viptv.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/**
 * Keeps D-pad Up/Down inside one vertical column of controls.
 *
 * Compose's geometric focus search is happy to jump from the last row of a
 * settings page across to the sidebar's last item, because that is the nearest
 * control in that direction. On a TV that reads as the remote "falling out" of
 * the page. Rows in a [TvFocusColumn] register themselves, and each row clamps
 * Up at the top and Down at the bottom; Left/Right keep their explicit
 * destinations (the sidebar and the page edge).
 */
internal class TvColumnFocusState {
    val requesters = mutableStateListOf<FocusRequester>()

    fun upFor(own: FocusRequester): FocusRequester {
        val index = requesters.indexOf(own)
        return if (index <= 0) FocusRequester.Cancel else requesters[index - 1]
    }

    fun downFor(own: FocusRequester): FocusRequester {
        val index = requesters.indexOf(own)
        return if (index < 0 || index >= requesters.lastIndex) FocusRequester.Cancel else requesters[index + 1]
    }
}

internal val LocalTvColumnFocus = compositionLocalOf<TvColumnFocusState?> { null }

@Composable
internal fun TvFocusColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = remember { TvColumnFocusState() }
    CompositionLocalProvider(LocalTvColumnFocus provides state) {
        // Settings pages must scroll: a plain column lays rows out below the
        // window edge on wide ratios while they stay D-pad-focusable.
        Column(modifier.verticalScroll(rememberScrollState())) { content() }
    }
}

/** Registers an item with the enclosing [TvFocusColumn]; a no-op elsewhere. */
@Composable
internal fun rememberTvColumnItem(own: FocusRequester): Pair<FocusRequester?, FocusRequester?> {
    val column = LocalTvColumnFocus.current
    DisposableEffect(column, own) {
        column?.requesters?.add(own)
        onDispose { column?.requesters?.remove(own) }
    }
    if (column == null) return null to null
    // Reading the list keeps the clamp correct if rows appear or disappear.
    val index = column.requesters.indexOf(own)
    val up = if (index <= 0) FocusRequester.Cancel else column.requesters.getOrNull(index - 1)
    val down = if (index < 0 || index >= column.requesters.lastIndex) FocusRequester.Cancel
        else column.requesters.getOrNull(index + 1)
    return up to down
}
