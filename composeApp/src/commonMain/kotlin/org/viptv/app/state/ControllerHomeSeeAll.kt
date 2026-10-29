package org.viptv.app.state

import org.viptv.app.features.home.CONTINUE_ROW
import org.viptv.app.features.home.MY_LIST_ROW
import org.viptv.engine.wire.HomeRow

data class RowSeeAll(val screen: Screen, val title: String, val caption: String, val count: Int? = null)

fun ApplicationState.rowSeeAll(row: HomeRow): RowSeeAll? = when (row.id) {
    CONTINUE_ROW -> continueWatchingSeeAll(row)
    MY_LIST_ROW -> myListSeeAll(row)
    else -> null
}

private fun ApplicationState.continueWatchingSeeAll(row: HomeRow): RowSeeAll? {
    val list = continueWatching?.takeIf { it.profile == selectedProfile } ?: return null
    if (list.next == null && list.cards.size <= row.cards.size) return null
    val count = list.cards.size.takeIf { list.next == null }
    return RowSeeAll(Screen.ContinueWatching, row.title,
        count?.let { "${titleCount(it)} in progress" } ?: "Everything in progress", count)
}

private fun ApplicationState.myListSeeAll(row: HomeRow): RowSeeAll? {
    if (library.size <= row.cards.size) return null
    return RowSeeAll(Screen.Library, row.title, "${titleCount(library.size)} saved", library.size)
}

internal fun titleCount(count: Int) = "$count ${if (count == 1) "title" else "titles"}"
