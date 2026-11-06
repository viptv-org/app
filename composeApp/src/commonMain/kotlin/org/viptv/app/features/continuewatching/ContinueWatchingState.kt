package org.viptv.app.features.continuewatching

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import org.viptv.app.components.ListPageState
import org.viptv.app.components.PageState
import org.viptv.app.components.PageStateAction
import org.viptv.app.components.PageStateLayout

/** The page's own empty and failed states, drawn with the block every list page uses. */
@Composable
internal fun ContinueWatchingState(state: ListPageState, error: String?, retry: () -> Unit, home: () -> Unit,
    modifier: Modifier = Modifier, layout: PageStateLayout = PageStateLayout.Phone, scale: Float = 1f,
    focus: FocusRequester? = null, enabled: Boolean = true) {
    val failed = state == ListPageState.Failed
    PageState(
        icon = if (failed) Lucide.CloudOff else Lucide.Play,
        title = if (failed) CONTINUE_FAILED_TITLE else CONTINUE_EMPTY_TITLE,
        message = if (failed) continueWatchingFailureMessage(error) else CONTINUE_EMPTY_MESSAGE,
        actions = listOf(PageStateAction(if (failed) CONTINUE_RETRY_ACTION else CONTINUE_EMPTY_ACTION, primary = true,
            activate = if (failed) retry else home)),
        modifier = modifier, layout = layout, scale = scale, focus = focus, enabled = enabled)
}