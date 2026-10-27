package org.viptv.app.features.sources

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.SourceGroups
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

/** Copy from `viptv-design-system/copy.md` (Source groups). */
internal const val BEST_MATCHES_HEADER = "Best matches"
internal const val ALL_SOURCES_HEADER = "All sources"

/**
 * One visible source row of the grouped picker. [header] labels the group this row
 * opens; it is drawn inside the row's list item so focus that lands on the row
 * brings the header into view with it.
 */
@Immutable
internal data class SourceListRow(val source: SourceCandidate, val header: String? = null, val bestMatch: Boolean = false)

/**
 * SRC-BEST-MATCH-001 Source groups over the rows the provider and quality filters left
 * visible. Membership comes from [groups] and never from position, so a filter that
 * hides the Best match row leaves no visible marker. Without a visible Best matches
 * row both headers hide and the rows read as one ranked list.
 */
internal fun sourceListRows(visible: List<SourceCandidate>, groups: SourceGroups): List<SourceListRow> {
    val members = groups.bestMatches.toSet()
    val best = visible.filter { it.id in members }
    if (best.isEmpty()) return visible.map { SourceListRow(it) }
    val rest = visible.filter { it.id !in members }
    return best.mapIndexed { index, source ->
        SourceListRow(source, BEST_MATCHES_HEADER.takeIf { index == 0 }, source.id == groups.bestMatchId)
    } + rest.mapIndexed { index, source -> SourceListRow(source, ALL_SOURCES_HEADER.takeIf { index == 0 }) }
}

/**
 * A group label: uppercase, exposed as a heading, never focusable and never
 * activating the row it sits above (its presses are consumed here). Geometry is
 * provisional until the design board exists.
 */
@Composable
internal fun SourceGroupHeader(label: String, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Palette.tertiary) {
    Text(label.uppercase(), modifier
        .semantics(mergeDescendants = true) { heading() }
        .pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        },
        color = color, fontSize = fontSize, letterSpacing = (fontSize.value * .08f).sp, fontWeight = FontWeight.Bold)
}

/**
 * Keeps the list where the viewer left it while rows re-rank (SRC-BEST-MATCH-001,
 * Progressive ranking 3-5). Call it in composition, before the list, with the row
 * keys in list order.
 *
 * - [navigated] is the row the viewer moved TV or keyboard focus to: it keeps its
 *   on-screen offset while rows move around it, even when it changes group.
 * - Otherwise a list still at its top stays there, so a better row arriving first
 *   is seen; a scrolled list keeps its first fully visible row in place.
 * - A touch scroll in progress is left alone.
 *
 * [leading] counts the list items before the first row (an error line).
 */
@Composable
internal fun KeepSourceListPosition(state: LazyListState, keys: List<String>, navigated: String?, leading: Int = 0) {
    val previous = remember(state) { arrayOfNulls<List<String>>(1) }
    val before = previous[0]
    previous[0] = keys
    if (before == null || before == keys) return
    // The layout still describes the previous rows while this composition runs.
    Snapshot.withoutReadObservation {
        if (state.isScrollInProgress) return@withoutReadObservation
        val layout = state.layoutInfo
        fun index(key: Any) = (key as? String)?.let(keys::indexOf) ?: -1
        val focusedItem = layout.visibleItemsInfo.firstOrNull { navigated != null && it.key == navigated && index(it.key) >= 0 }
        if (focusedItem == null && state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            state.requestScrollToItem(0)
            return@withoutReadObservation
        }
        val anchor = focusedItem
            ?: layout.visibleItemsInfo.firstOrNull { it.offset >= layout.viewportStartOffset && index(it.key) >= 0 }
            ?: return@withoutReadObservation
        // Item offsets count from the end of the leading content padding.
        state.requestScrollToItem(index(anchor.key) + leading, -anchor.offset)
    }
}
