package org.viptv.app.features.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.ListPageState
import org.viptv.app.components.PageState
import org.viptv.app.components.PageStateAction
import org.viptv.app.components.pageStateLayout
import org.viptv.app.state.Screen
import org.viptv.engine.wire.HomeSnapshot

/** Home's empty and failed states: one page state block, never a card and never the page's action failures. */
@Composable
internal fun HomeStatus(home: HomeSnapshot?, error: String?, busy: Boolean, retry: () -> Unit,
    navigate: (Screen) -> Unit, modifier: Modifier = Modifier, compact: Boolean = true,
    television: Boolean = false, scale: Float = 1f) {
    val spread = homePageState(home, busy, error)
    if (spread != ListPageState.Failed && spread != ListPageState.Empty) return
    val failed = spread == ListPageState.Failed
    val noCatalogs = !failed && home?.hasAnyRow() != true
    val focus = remember { FocusRequester() }
    PageState(
        icon = when { failed -> Lucide.CloudOff; noCatalogs -> Lucide.LayoutGrid; else -> Lucide.Film },
        title = when { failed -> HOME_FAILED_TITLE; noCatalogs -> HOME_NO_CATALOGS_TITLE; else -> HOME_NO_TITLES_TITLE },
        message = when { failed -> homeFailureMessage(home, error); noCatalogs -> HOME_NO_CATALOGS_MESSAGE; else -> HOME_NO_TITLES_MESSAGE },
        actions = listOf(if (noCatalogs) PageStateAction(HOME_NO_CATALOGS_ACTION, primary = true,
            activate = { navigate(Screen.Settings) })
        else PageStateAction(HOME_RETRY_ACTION, primary = true, activate = retry)),
        modifier = modifier, layout = pageStateLayout(television, compact), scale = scale,
        focus = focus, enabled = !busy)
}