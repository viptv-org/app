package org.viptv.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

internal class TvColumnFocusState {
    internal class Entry(val requester: FocusRequester) { var top = Float.MAX_VALUE }

    private val entries = mutableListOf<Entry>()

    fun add(entry: Entry) { entries += entry }

    fun remove(entry: Entry) { entries -= entry }

    // Rows can appear above rows that are already composed, so order is the layout order, not the order of arrival.
    private fun ordered(): List<FocusRequester> = entries.sortedBy { it.top }.map { it.requester }

    fun upFor(own: FocusRequester): FocusRequester {
        val ordered = ordered()
        val index = ordered.indexOf(own)
        return if (index <= 0) FocusRequester.Cancel else ordered[index - 1]
    }

    fun downFor(own: FocusRequester): FocusRequester {
        val ordered = ordered()
        val index = ordered.indexOf(own)
        return if (index < 0 || index >= ordered.lastIndex) FocusRequester.Cancel else ordered[index + 1]
    }
}

internal val LocalTvColumnFocus = compositionLocalOf<TvColumnFocusState?> { null }

@Composable
internal fun TvFocusColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val state = remember { TvColumnFocusState() }
    CompositionLocalProvider(LocalTvColumnFocus provides state) {
        // Settings pages must scroll: a plain column lays rows out below the window edge on wide ratios while they stay D-pad-focusable.
        org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(modifier.verticalScroll(rememberScrollState())) { content() } }
    }
}

internal class TvColumnItem(private val column: TvColumnFocusState?, private val own: FocusRequester,
    private val entry: TvColumnFocusState.Entry) {
    val position: Modifier = Modifier.onGloballyPositioned { entry.top = it.positionInRoot().y }

    fun constrain(properties: FocusProperties) {
        if (column == null) return
        properties.up = column.upFor(own)
        properties.down = column.downFor(own)
    }
}

@Composable
internal fun rememberTvColumnItem(own: FocusRequester): TvColumnItem {
    val column = LocalTvColumnFocus.current
    val entry = remember(own) { TvColumnFocusState.Entry(own) }
    DisposableEffect(column, entry) {
        column?.add(entry)
        onDispose { column?.remove(entry) }
    }
    return remember(column, own, entry) { TvColumnItem(column, own, entry) }
}
