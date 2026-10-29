package org.viptv.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Box(modifier.pullToRefresh(refreshing, state, enabled) {
        running++
        scope.launch { try { refresh() } finally { running-- } }
    }) {
        content()
        PullToRefreshDefaults.Indicator(state, refreshing, Modifier.align(Alignment.TopCenter))
    }
}
