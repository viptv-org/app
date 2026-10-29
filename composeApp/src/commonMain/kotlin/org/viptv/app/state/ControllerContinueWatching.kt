package org.viptv.app.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.viptv.app.connection.*
import org.viptv.app.components.announcing
import org.viptv.app.features.home.CONTINUE_ROW
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationPage
import org.viptv.engine.wire.HomeRow
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.Scope

/** Home's Continue Watching row holds the server's first 12 entries (backend `engine_v3/home.rs`); a shorter row is the whole list. */
internal const val HOME_CONTINUE_ROW_CAP = 12
/** One page of the full list: six rows of the TV grid. */
internal const val CONTINUE_PAGE = 24
/** The server's page ceiling (`continuation/page.rs`). */
private const val CONTINUE_PAGE_MAX = 100

/** The full Continue Watching list a profile has loaded (app#114); [next] is the server's cursor for the rest. */
data class ContinueWatchingList(val profile: String, val cards: List<Card>, val next: String?)

/** What Home's Continue Watching end card says: the list's size once it is fully known. */
data class ContinueWatchingMore(val count: Int?)

/** The Home check last made, so a Home revision reads the list once. */
internal data class ContinueCheck(val profile: String, val home: Scope, val revision: Long)

/** Home's Continue Watching row, when it leads to the full list. */
internal fun HomeSnapshot.continueRow(): HomeRow? = rows.find { it.id == CONTINUE_ROW && it.opensContinueWatching() }

/**
 * Whether Home's Continue Watching row ends with "See all": only when the full list holds more than
 * the row shows. The local engine has no full list, so its row never does.
 */
fun ApplicationState.continueWatchingMore(): ContinueWatchingMore? {
    val row = home?.continueRow() ?: return null
    val list = continueWatching?.takeIf { it.profile == selectedProfile } ?: return null
    if (list.next == null && list.cards.size <= row.cards.size) return null
    return ContinueWatchingMore(list.cards.size.takeIf { list.next == null })
}

/**
 * Home changed: when its Continue Watching row is full (or the list is already loaded), read the
 * list's first page in the background. The row learns whether it ends with "See all", and the page
 * then opens with its cards. Once per Home revision; a failure leaves the row as it is.
 */
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

/** The quiet lane rebuilt Home while the list is on display: re-read it now, and count it as this Home's check. */
internal suspend fun ApplicationController.reloadVisibleContinueWatching(owner: QuietOwner) {
    val remote = owner.remote ?: return
    mutable.value.home?.let { continueChecked = ContinueCheck(owner.profile, it.scope.owner(), it.revision) }
    reloadContinueWatching(remote, owner.profile)
}

/** Re-read the loaded part of the list (at least a page) in place: no busy cover, the shown cards stay until it lands. */
private suspend fun ApplicationController.reloadContinueWatching(remote: RemoteConnection, profile: String) {
    val shown = mutable.value.continueWatching?.takeIf { it.profile == profile }
    val limit = (shown?.cards?.size ?: 0).coerceIn(CONTINUE_PAGE, CONTINUE_PAGE_MAX)
    val page = remote.continueWatching(profile, 0, limit)
    if (connection !== remote || mutable.value.selectedProfile != profile) return
    val current = mutable.value.continueWatching?.takeIf { it.profile == profile }
    mutable.value = mutable.value.copy(continueWatching = current.refreshedBy(profile, page, limit),
        // Every read carries the next episode each caught-up card waits for (app#132).
        continueNext = mutable.value.continueNext.announcing(page.entries))
}

/**
 * A fresh first [limit] entries replace the list's first [limit]. A viewer who paged past the server's
 * page ceiling keeps the rest and its cursor, so a refresh never shrinks the grid under focus.
 */
internal fun ContinueWatchingList?.refreshedBy(profile: String, page: ContinuationPage, limit: Int): ContinueWatchingList {
    val fresh = ContinueWatchingList(profile, page.entries.map { it.card }.distinctBy(::cardLookupKey), page.next_cursor)
    if (this == null || this.profile != profile || cards.size <= limit || page.next_cursor == null) return fresh
    return fresh.copy(cards = (fresh.cards + cards.drop(limit)).distinctBy(::cardLookupKey), next = next)
}

/** Opening the page: a list Home already read shows at once; otherwise its first page loads under the busy state. */
internal fun ApplicationController.loadContinueWatching(navigation: Long = navigationGeneration) {
    val profile = mutable.value.selectedProfile ?: return
    if (mutable.value.continueWatching?.profile == profile) return
    runPage {
        val remote = connection ?: throw LocalFailure("Continue Watching is unavailable on this device.")
        val page = readCurrentPage(navigation) { remote.continueWatching(profile, 0, CONTINUE_PAGE) } ?: return@runPage
        mutable.value = mutable.value.copy(continueWatching = null.refreshedBy(profile, page, CONTINUE_PAGE),
            continueNext = mutable.value.continueNext.announcing(page.entries))
    }
}

/** The next page, as the viewer nears the end of the grid. */
fun ApplicationController.continueWatchingMore() {
    val profile = mutable.value.selectedProfile ?: return
    val list = mutable.value.continueWatching?.takeIf { it.profile == profile } ?: return
    val cursor = list.next ?: return
    val offset = cursor.toIntOrNull() ?: return
    val remote = connection ?: return
    val navigation = navigationGeneration
    runPage {
        val page = readCurrentPage(navigation) { remote.continueWatching(profile, offset, CONTINUE_PAGE) } ?: return@runPage
        // A quiet reload that landed meanwhile owns the list; this page belonged to the old one.
        val shown = mutable.value.continueWatching?.takeIf { it.profile == profile && it.next == cursor } ?: return@runPage
        val merged = (shown.cards + page.entries.map { it.card }).distinctBy(::cardLookupKey)
        // A page that adds nothing must not re-arm the same cursor forever.
        val next = page.next_cursor?.takeIf { merged.size > shown.cards.size && it != cursor }
        mutable.value = mutable.value.copy(continueWatching = shown.copy(cards = merged, next = next),
            continueNext = mutable.value.continueNext.announcing(page.entries))
    }
}
