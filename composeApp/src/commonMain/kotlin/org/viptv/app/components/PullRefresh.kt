package org.viptv.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullRefresh(touch: Boolean, enabled: Boolean, refresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!touch) return content()
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    // Pulls in flight: leaving the page mid-refresh and pulling again must not end the second one early.
    var running by remember { mutableIntStateOf(0) }
    val refreshing = enabled && running > 0
    val reduced = LocalReducedMotion.current
    Box(modifier.pullToRefresh(refreshing, state, enabled) {
        running++
        scope.launch { try { refresh() } finally { running-- } }
    }) {
        content()
        PullRefreshIndicator(state, refreshing, reduced, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun PullRefreshIndicator(state: PullToRefreshState, refreshing: Boolean, reduced: Boolean, modifier: Modifier = Modifier) {
    if (reduced && refreshing) PullStillArc(state, refreshing, modifier)
    else PullToRefreshDefaults.Indicator(state, refreshing, modifier)
}

// The arc is the busy report, so under reduce motion it stays visible as a still arc instead of rotating.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullStillArc(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier) {
    PullToRefreshDefaults.IndicatorBox(state, refreshing, modifier, maxDistance = PullToRefreshDefaults.IndicatorMaxDistance,
        shape = PullToRefreshDefaults.indicatorShape, containerColor = PullToRefreshDefaults.indicatorContainerColor,
        elevation = PullToRefreshDefaults.Elevation) {
        CircularProgressIndicator(progress = { 0.2f }, Modifier.size(32.dp),
            color = PullToRefreshDefaults.indicatorColor, strokeWidth = 2.5.dp)
    }
}
