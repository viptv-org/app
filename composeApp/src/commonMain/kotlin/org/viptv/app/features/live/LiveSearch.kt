package org.viptv.app.features.live

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.viptv.app.components.TvTextField
import org.viptv.app.features.search.SearchField

internal const val LiveSearchPlaceholder = "Search channels"
internal const val LiveSearchLabel = "Search channels"
internal const val LiveNoSearchMatch = "No channels match your search."
internal const val LiveClearSearch = "Clear search"

private const val LIVE_SEARCH_SETTLE_MILLIS = 250L

@Stable
internal class LiveSearchDraft(initial: String) {
    var text by mutableStateOf(initial)
    internal var asked by mutableStateOf(initial)
    internal var changed: (String) -> Unit = {}
    val typing: Boolean get() = text.trim() != asked
    fun matches(channel: LiveChannel): Boolean = !typing || channel.card.title.contains(text.trim(), ignoreCase = true)
    fun ask() { if (typing) { asked = text.trim(); changed(asked) } }
    fun clear() { text = ""; asked = ""; changed("") }
}

@Composable
internal fun rememberLiveSearchDraft(search: String, changed: (String) -> Unit): LiveSearchDraft {
    val draft = remember { LiveSearchDraft(search) }
    draft.changed = changed
    LaunchedEffect(search) { if (search != draft.asked) { draft.asked = search; draft.text = search } }
    LaunchedEffect(draft.text) {
        if (!draft.typing) return@LaunchedEffect
        delay(LIVE_SEARCH_SETTLE_MILLIS)
        draft.ask()
    }
    return draft
}

@Composable
internal fun LiveSearchField(draft: LiveSearchDraft, modifier: Modifier = Modifier, maxWidth: Boolean = false) {
    SearchField(draft.text, { draft.text = it }, modifier.then(if (maxWidth) Modifier.widthIn(max = 460.dp) else Modifier),
        placeholder = LiveSearchPlaceholder, label = LiveSearchLabel, autoFocus = false)
}

@Composable
internal fun TvLiveSearchField(draft: LiveSearchDraft, scale: Float, modifier: Modifier = Modifier,
    requester: FocusRequester? = null, down: FocusRequester? = null) {
    TvTextField(draft.text, { draft.text = it }, scale, modifier, placeholder = LiveSearchPlaceholder, imeAction = ImeAction.Search,
        submit = { draft.ask() }, requester = requester, down = down)
}
