package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import org.viptv.app.connection.*
import org.viptv.app.features.home.CONTINUE_ROW
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.RowState
import org.viptv.engine.wire.Scope

internal const val HOME_CONTINUE_ROW_CAP = 12
internal const val CONTINUE_PAGE = 24
private const val CONTINUE_PAGE_MAX = 100

data class ContinueWatchingList(val profile: String, val cards: List<Card>, val next: String?)

// Held until the viewer undoes the removal or removes another title.
class ContinueUndo(val profile: String, val card: Card, val index: Int) {
    @Volatile var identity: MediaIdentity = card.identity
}

internal data class ContinueCheck(val profile: String, val home: Scope, val revision: Long)

internal fun ApplicationController.adoptContinuation(page: ContinuationPage?) {
    if (page == null) return
    mutable.value = mutable.value.copy(continueNext = mutable.value.continueNext.announced(page.entries))
}

internal fun HomeSnapshot.continueRow(): HomeRow? = rows.find { it.id == CONTINUE_ROW && it.opensContinueWatching() }

fun ApplicationController.checkContinueWatching() {
    val state = mutable.value
    val profile = state.selectedProfile ?: return
    val remote = connection ?: return
    if (local != null) return
    val home = state.home ?: return
    val loaded = state.continueWatching?.profile == profile
    if (!loaded && (home.continueRow()?.cards?.size ?: 0) < HOME_CONTINUE_ROW_CAP) return
    val check = ContinueCheck(profile, home.scope.owner(), home.revision)
    if (continueChecked == check) return
    continueChecked = check
    scope.launch {
        try { reloadContinueWatching(remote, profile) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if (error is RemoteFailure && error.status == 401 && connection === remote && mutable.value.selectedProfile == profile)
                expireSession(remote, null)
        }
    }
}

internal suspend fun ApplicationController.reloadVisibleContinueWatching(owner: QuietOwner) {
    val remote = owner.remote ?: return
    mutable.value.home?.let { continueChecked = ContinueCheck(owner.profile, it.scope.owner(), it.revision) }
    reloadContinueWatching(remote, owner.profile)
}

private suspend fun ApplicationController.reloadContinueWatching(remote: RemoteConnection, profile: String) {
    val shown = mutable.value.continueWatching?.takeIf { it.profile == profile }
    val limit = (shown?.cards?.size ?: 0).coerceIn(CONTINUE_PAGE, CONTINUE_PAGE_MAX)
    val page = remote.continueWatching(profile, 0, limit)
    if (connection !== remote || mutable.value.selectedProfile != profile) return
    val current = mutable.value.continueWatching?.takeIf { it.profile == profile }
    mutable.value = mutable.value.copy(continueWatching = current.refreshedBy(profile, page, limit),
        continueNext = mutable.value.continueNext.announced(page.entries))
}

internal fun ContinueWatchingList?.refreshedBy(profile: String, page: ContinuationPage, limit: Int): ContinueWatchingList {
    val fresh = ContinueWatchingList(profile, page.entries.map { it.card }.distinctBy(::cardLookupKey), page.next_cursor)
    if (this == null || this.profile != profile || cards.size <= limit || page.next_cursor == null) return fresh
    return fresh.copy(cards = (fresh.cards + cards.drop(limit)).distinctBy(::cardLookupKey), next = next)
}

internal fun ApplicationController.loadContinueWatching(navigation: Long = navigationGeneration) {
    val profile = mutable.value.selectedProfile ?: return
    if (mutable.value.continueWatching?.profile == profile) return
    runPage {
        val remote = connection ?: throw LocalFailure("Continue Watching is unavailable on this device.")
        val page = readCurrentPage(navigation) { remote.continueWatching(profile, 0, CONTINUE_PAGE) } ?: return@runPage
        mutable.value = mutable.value.copy(continueWatching = null.refreshedBy(profile, page, CONTINUE_PAGE),
            continueNext = mutable.value.continueNext.announced(page.entries))
    }
}

fun ApplicationController.continueWatchingMore() {
    val profile = mutable.value.selectedProfile ?: return
    val list = mutable.value.continueWatching?.takeIf { it.profile == profile } ?: return
    val cursor = list.next ?: return
    val offset = cursor.toIntOrNull() ?: return
    val remote = connection ?: return
    val navigation = navigationGeneration
    runPage {
        val page = readCurrentPage(navigation) { remote.continueWatching(profile, offset, CONTINUE_PAGE) } ?: return@runPage
        val shown = mutable.value.continueWatching?.takeIf { it.profile == profile && it.next == cursor } ?: return@runPage
        val merged = (shown.cards + page.entries.map { it.card }).distinctBy(::cardLookupKey)
        val next = page.next_cursor?.takeIf { merged.size > shown.cards.size && it != cursor }
        mutable.value = mutable.value.copy(continueWatching = shown.copy(cards = merged, next = next),
            continueNext = mutable.value.continueNext.announced(page.entries))
    }
}

internal fun ApplicationState.withContinueCardRemoved(card: Card): ApplicationState {
    val list = continueWatching ?: return this
    val key = cardLookupKey(card)
    val index = list.cards.indexOfFirst { cardLookupKey(it) == key }
    if (index < 0) return this
    return copy(
        continueWatching = list.copy(cards = list.cards.filterIndexed { at, _ -> at != index }),
        home = home?.let { snapshot -> snapshot.copy(rows = snapshot.rows.map { row ->
            if (row.id != CONTINUE_ROW) row
            else row.copy(cards = row.cards.filterNot { cardLookupKey(it) == key }).let {
                if (it.cards.isEmpty()) it.copy(state = RowState.EMPTY) else it
            } }) },
        continueUndo = ContinueUndo(list.profile, card, index))
}

internal fun ApplicationState.withContinueCardRestored(undo: ContinueUndo): ApplicationState {
    val key = cardLookupKey(undo.card)
    fun List<Card>.restored(): List<Card> = if (any { cardLookupKey(it) == key }) this
        else toMutableList().apply { add(undo.index.coerceIn(0, size), undo.card) }
    return copy(
        continueWatching = continueWatching?.takeIf { it.profile == undo.profile }
            ?.let { list -> list.copy(cards = list.cards.restored()) } ?: continueWatching,
        home = home?.let { snapshot -> snapshot.copy(rows = snapshot.rows.map { row ->
            if (row.id != CONTINUE_ROW || row.cards.size >= HOME_CONTINUE_ROW_CAP) row
            else row.copy(cards = row.cards.restored(), state = RowState.READY) }) },
        continueUndo = null)
}

fun ApplicationController.removeFromContinueWatching(card: Card) {
    val profile = mutable.value.selectedProfile ?: return
    val remote = connection ?: return
    if (mutable.value.continueWatching?.profile != profile) return
    updateState { it.withContinueCardRemoved(card) }
    val undo = mutable.value.continueUndo?.takeIf { it.profile == profile && cardLookupKey(it.card) == cardLookupKey(card) } ?: return
    continueVisibilityJob = scope.launch {
        try {
            undo.identity = remote.hideFromContinueWatching(profile, card)
        }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            updateState { state ->
                if (state.continueUndo !== undo) state
                else state.withContinueCardRestored(undo).copy(error = failureMessage(error))
            }
        }
    }
}

fun ApplicationController.undoRemoveFromContinueWatching() {
    val undo = mutable.value.continueUndo ?: return
    val remote = connection
    val pending = continueVisibilityJob
    continueVisibilityJob = null
    updateState { it.withContinueCardRestored(undo) }
    if (remote == null) return
    scope.launch {
        pending?.join()
        try { remote.showInContinueWatching(undo.profile, undo.identity) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { updateState { it.copy(error = failureMessage(error)) } }
    }
}
