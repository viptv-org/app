package org.viptv.app.state

import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card

// components.md §9: one notice at a time, a removal carries Undo for 8 s, a plain notice lasts 5 s.
enum class NoticeAction { Undo }

data class AppNotice(val text: String, val action: NoticeAction? = null, val serial: Long = 0L)

internal const val ADDED_TO_MY_LIST = "Added to My List"
internal const val REMOVED_FROM_MY_LIST = "Removed from My List"
internal const val REMOVED_FROM_CONTINUE = "Removed from Continue Watching"

// Undo needs the row's old place, so it returns where it was rather than at the end.
data class MyListUndo(val profile: String, val card: Card, val index: Int)

internal fun ApplicationState.withRemovalNotice(text: String, serial: Long, myList: MyListUndo? = null,
    continueWatching: ContinueUndo? = null): ApplicationState =
    copy(notice = AppNotice(text, NoticeAction.Undo, serial), myListUndo = myList, continueUndo = continueWatching)

internal fun ApplicationState.withNoticeDismissed(): ApplicationState =
    copy(notice = null, myListUndo = null, continueUndo = null)

internal fun ApplicationState.savedIndex(card: Card): Int {
    val key = cardLookupKey(card)
    libraryPage?.cards?.indexOfFirst { cardLookupKey(it) == key }?.takeIf { it >= 0 }?.let { return it }
    return library.indexOfFirst { cardLookupKey(it) == key }
}