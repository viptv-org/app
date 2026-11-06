package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

// A list page's body shows the rows, the failure once a request has settled with one, the skeleton while the
// answer is still coming, and an empty state only after that answer.
internal enum class ListPageState { Loading, Failed, Empty, Content }

internal const val ListsLoading = "Loading"

internal fun listPageState(shown: Int, busy: Boolean, error: String?, answered: Boolean = !busy): ListPageState = when {
    shown > 0 -> ListPageState.Content
    error != null && !busy -> ListPageState.Failed
    !answered -> ListPageState.Loading
    else -> ListPageState.Empty
}

@Composable
internal fun ListSkeletonTile(modifier: Modifier, radius: Dp = 8.dp, raised: Boolean = false, shimmer: Boolean = true) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(if (raised) Palette.raised else Palette.surface)
        .then(if (shimmer && !LocalReducedMotion.current) rememberTvLoadingShimmer(1f) else Modifier))
}

// Poster tiles in the grid the page itself lays out: three on a phone, as many as fit at desk widths.
@Composable
internal fun PosterSkeleton(phone: Boolean, modifier: Modifier = Modifier, columns: Int? = null,
    gap: Dp = if (phone) 10.dp else 12.dp, rows: Int = 3, tile: Dp = 176.dp) {
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = ListsLoading }) {
        val fit = columns ?: if (phone) 3 else ((maxWidth + gap) / (tile + gap)).toInt().coerceAtLeast(2)
        val width = (maxWidth - gap * (fit - 1)) / fit
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(fit) { ListSkeletonTile(Modifier.width(width).aspectRatio(2f / 3f)) }
                }
            }
        }
    }
}

// The calendar's release rows: a time, a poster and two lines of copy.
@Composable
internal fun ReleaseRowSkeleton(phone: Boolean, modifier: Modifier = Modifier, rows: Int = 4) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = ListsLoading },
        verticalArrangement = Arrangement.spacedBy(if (phone) 4.dp else 18.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth().height(if (phone) 100.dp else 86.dp)
                .clip(RoundedCornerShape(if (phone) 16.dp else 18.dp)).background(Palette.surface)
                .then(if (LocalReducedMotion.current) Modifier else rememberTvLoadingShimmer(1f))
                .padding(horizontal = if (phone) 12.dp else 28.dp, vertical = if (phone) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 28.dp)) {
                ListSkeletonTile(Modifier.width(if (phone) 62.dp else 90.dp).height(12.dp), radius = 6.dp, raised = true, shimmer = false)
                ListSkeletonTile(Modifier.height(if (phone) 54.dp else 60.dp).aspectRatio(2f / 3f), radius = 6.dp, raised = true, shimmer = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (phone) 6.dp else 10.dp)) {
                    ListSkeletonTile(Modifier.fillMaxWidth(0.55f).height(if (phone) 14.dp else 18.dp), radius = 4.dp, raised = true, shimmer = false)
                    ListSkeletonTile(Modifier.fillMaxWidth(0.35f).height(if (phone) 11.dp else 14.dp), radius = 4.dp, raised = true, shimmer = false)
                }
            }
        }
    }
}