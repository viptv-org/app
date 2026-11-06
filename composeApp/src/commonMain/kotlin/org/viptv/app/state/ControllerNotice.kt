package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.cardLookupKey
import org.viptv.app.connection.saveFavorite
import org.viptv.app.features.home.MY_LIST_ROW
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.RowState

internal fun ApplicationController.showNotice(text: String, action: NoticeAction? = null) {
    updateState { it.copy(notice = AppNotice(text, action, ++noticeSerial)) }
}

fun ApplicationController.dismissNotice() {
    updateState { it.withNoticeDismissed() }
}

// The notice's own action: the last removal comes back, whichever list it left.
fun ApplicationController.undoRemoval() {
    val myList = mutable.value.myListUndo
    if (myList != null) { undoMyListRemoval(myList); return }
    if (mutable.value.continueUndo == null) dismissNotice()
    undoRemoveFromContinueWatching()
}

internal fun ApplicationController.undoMyListRemoval(undo: MyListUndo) {
    updateState { it.withSavedCardRestored(undo) }
    val profile = mutable.value.selectedProfile ?: return
    scope.launch {
        try {
            local?.saveFavorite(profile, undo.card) ?: requireNotNull(connection).saveFavorite(profile, undo.card)
            loadLibrary()
            reloadMyListAfterWrite()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { updateState { it.copy(error = failureMessage(error)) } }
    }
}

internal fun ApplicationState.withSavedCardRestored(undo: MyListUndo): ApplicationState {
    if (undo.profile != selectedProfile) return withNoticeDismissed()
    val key = cardLookupKey(undo.card)
    fun List<Card>.restored(): List<Card> = if (any { cardLookupKey(it) == key }) this
        else toMutableList().apply { add(undo.index.coerceIn(0, size), undo.card) }
    return copy(
        library = library.restored(),
        libraryPage = libraryPage?.let { page -> page.copy(cards = page.cards.restored()) },
        home = home?.let { snapshot -> snapshot.copy(rows = snapshot.rows.map { row ->
            if (row.id != MY_LIST_ROW || row.provider_id != "library") row
            else row.copy(cards = row.cards.restored(), state = RowState.READY) }) },
        favoriteKeys = favoriteKeys?.let { keys -> keys + (undo.card.identity.kind to undo.card.identity.id) },
        notice = null, myListUndo = null, continueUndo = null)
}