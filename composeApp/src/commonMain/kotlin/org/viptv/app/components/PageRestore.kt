package org.viptv.app.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.flow.first

/** Where a list page was left: the item opened there and where it sat. */
@Stable
internal class PageRestoreState internal constructor() {
    internal var lastKey by mutableStateOf<String?>(null)
    internal var lastIndex by mutableIntStateOf(0)
    internal val focus = FocusRequester()

    internal fun mark(key: String, index: Int) {
        lastKey = key
        lastIndex = index
    }

    /** The opened item's index, or where it sat when the list has moved on; null until something opens. */
    internal fun anchor(count: Int, keyAt: (Int) -> String): Int? {
        if (count <= 0) return null
        val key = lastKey ?: return null
        return (0 until count).firstOrNull { keyAt(it) == key } ?: lastIndex.coerceIn(0, count - 1)
    }

    /** [anchor], or the first item for a page that focuses a card as soon as it opens. */
    internal fun landing(count: Int, keyAt: (Int) -> String): Int? =
        if (count <= 0) null else anchor(count, keyAt) ?: 0
}

private val PageRestoreSaver = listSaver<PageRestoreState, Any?>(
    save = { listOf(it.lastKey, it.lastIndex) },
    restore = { PageRestoreState().apply { lastKey = it[0] as String?; lastIndex = it[1] as Int } },
)

@Composable
internal fun rememberPageRestore(page: String): PageRestoreState =
    rememberSaveable(page, saver = PageRestoreSaver) { PageRestoreState() }

/** The anchored item carries the page's focus claim and remembers itself while it holds focus. */
internal fun Modifier.pageAnchor(restore: PageRestoreState, key: String, index: Int, target: Boolean): Modifier =
    (if (target) Modifier.focusRequester(restore.focus) else Modifier)
        .onFocusChanged { if (it.isFocused) restore.mark(key, index) }

@Composable
internal fun PageRestore(grid: LazyGridState, restore: PageRestoreState, claimOnEntry: Boolean = false, target: () -> Int?) {
    RestorePage(restore, claimOnEntry, target) { index ->
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == index }) grid.scrollToItem(index)
    }
}

@Composable
internal fun PageRestore(list: LazyListState, restore: PageRestoreState, claimOnEntry: Boolean = false, target: () -> Int?) {
    RestorePage(restore, claimOnEntry, target) { index ->
        if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
    }
}

// Runs once per visit: the page leaves composition on the way to Details.
@Composable
private fun RestorePage(restore: PageRestoreState, claimOnEntry: Boolean, target: () -> Int?, place: suspend (Int) -> Unit) {
    val latest = rememberUpdatedState(target)
    val input = LocalInputModeManager.current
    LaunchedEffect(restore) {
        val index = snapshotFlow { latest.value() }.first { it != null } ?: return@LaunchedEffect
        place(index)
        val claims = claimOnEntry || (restore.lastKey != null && input.inputMode == InputMode.Keyboard)
        if (!claims) return@LaunchedEffect
        input.requestInputMode(InputMode.Keyboard)
        repeat(30) {
            if (runCatching { restore.focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
}
