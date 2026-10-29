package org.viptv.app.features.sources

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.SourceGroups
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

internal const val BEST_MATCHES_HEADER = "Best matches"
internal const val ALL_SOURCES_HEADER = "All sources"
internal const val BEST_MATCH_MARKER = "Best match"

@Immutable
internal data class SourceListRow(val source: SourceCandidate, val header: String? = null, val bestMatch: Boolean = false)

internal fun sourceListRows(visible: List<SourceCandidate>, groups: SourceGroups): List<SourceListRow> {
    val members = groups.bestMatches.toSet()
    val best = visible.filter { it.id in members }
    if (best.isEmpty()) return visible.map { SourceListRow(it) }
    val rest = visible.filter { it.id !in members }
    return best.mapIndexed { index, source ->
        SourceListRow(source, BEST_MATCHES_HEADER.takeIf { index == 0 }, source.id == groups.bestMatchId)
    } + rest.mapIndexed { index, source -> SourceListRow(source, ALL_SOURCES_HEADER.takeIf { index == 0 }) }
}

@Composable
internal fun SourceGroupHeader(label: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Palette.tertiary,
    measured: (Int) -> Unit = {}) {
    Text(label.uppercase(), Modifier.onSizeChanged { measured(it.height) }.then(modifier)
        .semantics(mergeDescendants = true) { heading() }
        .pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        },
        color = color, fontSize = fontSize, letterSpacing = (fontSize.value * .08f).sp, fontWeight = FontWeight.Bold)
}

internal class SourceHeaderHeight { var pixels = 0 }

@Composable
internal fun KeepSourceListPosition(state: LazyListState, rows: List<SourceListRow>, navigated: String?,
    header: SourceHeaderHeight, leading: Int = 0) {
    val previous = remember(state) { arrayOfNulls<List<SourceListRow>>(1) }
    val before = previous[0]
    SideEffect { previous[0] = rows }
    if (before == null || before.map { it.source.id } == rows.map { it.source.id }) return
    Snapshot.withoutReadObservation {
        if (state.isScrollInProgress) return@withoutReadObservation
        val layout = state.layoutInfo
        fun index(key: Any) = rows.indexOfFirst { it.source.id == key }
        val focusedItem = layout.visibleItemsInfo.firstOrNull { navigated != null && it.key == navigated && index(it.key) >= 0 }
        if (focusedItem == null && state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            state.requestScrollToItem(0)
            return@withoutReadObservation
        }
        val anchor = focusedItem
            ?: layout.visibleItemsInfo.firstOrNull { it.offset >= layout.viewportStartOffset && index(it.key) >= 0 }
            ?: return@withoutReadObservation
        val target = index(anchor.key)
        fun headed(list: List<SourceListRow>) = list.firstOrNull { it.source.id == anchor.key }?.header != null
        val shift = (if (headed(rows)) header.pixels else 0) - (if (headed(before)) header.pixels else 0)
        state.requestScrollToItem(target + leading, shift - anchor.offset)
    }
}
