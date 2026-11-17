package org.viptv.app.features.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.viptv.app.components.TvTextField
import org.viptv.app.features.search.SearchField

// copy.md §Live — the channel search's words.
internal const val LiveSearchPlaceholder = "Search channels"
internal const val LiveSearchLabel = "Search channels"
internal const val LiveNoSearchMatch = "No channels match your search."
internal const val LiveClearSearch = "Clear search"

private const val LIVE_SEARCH_SETTLE_MILLIS = 250L

/** The field's text while it is being typed; the page's query follows once the typing pauses. */
@Composable
internal fun rememberLiveSearchDraft(search: String, changed: (String) -> Unit): MutableState<String> {
    val draft = remember { mutableStateOf(search) }
    LaunchedEffect(search) { if (draft.value != search) draft.value = search }
    LaunchedEffect(draft.value) {
        if (draft.value == search) return@LaunchedEffect
        delay(LIVE_SEARCH_SETTLE_MILLIS)
        changed(draft.value)
    }
    return draft
}

@Composable
internal fun LiveSearchField(term: String, changed: (String) -> Unit, modifier: Modifier = Modifier, maxWidth: Boolean = false) {
    SearchField(term, changed, modifier.then(if (maxWidth) Modifier.widthIn(max = 460.dp) else Modifier),
        placeholder = LiveSearchPlaceholder, label = LiveSearchLabel, autoFocus = false)
}

@Composable
internal fun TvLiveSearchField(term: String, changed: (String) -> Unit, scale: Float, modifier: Modifier = Modifier,
    requester: FocusRequester? = null, down: FocusRequester? = null) {
    TvTextField(term, changed, scale, modifier, placeholder = LiveSearchPlaceholder, imeAction = ImeAction.Search,
        submit = { changed(term) }, requester = requester, down = down)
}
