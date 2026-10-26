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

/**
 * Phone and tablet pull-to-refresh. Pulling down at the top of the page past
 * the Material 3 threshold runs [refresh] and shows the indicator until it
 * returns; a shorter pull does nothing. Without [touch] (TV, desktop, browser)
 * the page is composed exactly as before. [enabled] switches the gesture per
 * page without changing the composition, so a page that stays composed (Details
 * behind the sources sheet) is never rebuilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PullRefresh(touch: Boolean, enabled: Boolean, refresh: suspend () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!touch) return content()
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    Box(modifier.pullToRefresh(refreshing, state, enabled) {
        refreshing = true
        scope.launch { try { refresh() } finally { refreshing = false } }
    }) {
        content()
        PullToRefreshDefaults.Indicator(state, refreshing, Modifier.align(Alignment.TopCenter))
    }
}
