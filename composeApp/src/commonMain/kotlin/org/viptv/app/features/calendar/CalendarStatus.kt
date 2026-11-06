package org.viptv.app.features.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CalendarOff
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.PageState
import org.viptv.app.components.PageStateAction
import org.viptv.app.components.PageStateLayout

/** The Calendar's page states — a week with nothing scheduled, and a calendar with no source. */
@Composable
internal fun CalendarStatus(available: Boolean, manage: () -> Unit, modifier: Modifier = Modifier,
    layout: PageStateLayout = PageStateLayout.Phone, scale: Float = 1f, focus: FocusRequester? = null,
    enabled: Boolean = true) {
    PageState(
        icon = if (available) Lucide.CalendarDays else Lucide.CalendarOff,
        title = if (available) CALENDAR_EMPTY_WEEK else CALENDAR_UNAVAILABLE,
        message = "",
        actions = listOf(PageStateAction(CALENDAR_MANAGE, primary = true, activate = manage)),
        modifier = modifier, layout = layout, scale = scale, focus = focus, enabled = enabled)
}
