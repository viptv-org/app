package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.LibraryQuery
import kotlinx.coroutines.CancellationException

fun ApplicationController.libraryQuery(query: LibraryQuery) {
    updateState { next -> next.copy(libraryQuery = query.copy(offset = 0)) }
    rememberNavigation()
    run { loadLibraryPage(query.copy(offset = 0)) }
}
fun ApplicationController.libraryMore() = run {
    val offset = mutable.value.libraryPage?.next_cursor?.toLongOrNull() ?: return@run
    loadLibraryPage(mutable.value.libraryQuery.copy(offset = offset), append = true)
}
fun ApplicationController.refreshLibraryPage() = run { loadLibraryPage(refresh = true) }
internal fun ApplicationController.ensureLibraryPage() { if (mutable.value.libraryPage == null) refreshLibraryPage() }
// A My List write re-reads the page it is showing in place: the pages already loaded stay, and the title the
// write un-saved leaves them.
internal suspend fun ApplicationController.reloadMyListAfterWrite(unsaved: Card? = null) {
    if (mutable.value.screen != Screen.Library) return
    loadLibraryPage(refresh = true, removed = if (unsaved != null) 1 else 0)
    if (unsaved != null) updateState { it.withoutSavedCard(unsaved) }
}
fun ApplicationController.retryLibrary() {
    if (mutable.value.screen != Screen.Library) return
    run { loadLibraryPage() }
}
internal suspend fun ApplicationController.loadLibraryPage(query: LibraryQuery = mutable.value.libraryQuery, append: Boolean = false, refresh: Boolean = false, removed: Int = 0) {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val page = try {
        local?.libraryPage(profile, query) ?: requireNotNull(connection).libraryPage(profile, query)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        if (append && !(e is RemoteFailure && e.sessionExpired)) {
            updateState { it.copy(libraryPageError = failureMessage(e) ?: "Could not load more.") }
            return
        }
        throw e
    }
    val previous = mutable.value.libraryPage
    val retainPages = refresh && previous != null && previous.cards.size > query.limit && page.next_cursor != null
    // A removed row moves every later offset back by one; an opaque cursor cannot be moved, so the last loaded
    // page is read again for the host's own.
    val movedCursor = if (removed > 0) previous?.next_cursor?.movedBack(removed) else null
    val combined = when {
        append && previous != null -> page.copy(
            cards = (previous.cards + page.cards).distinctBy(::cardLookupKey),
            statistics = (previous.statistics + page.statistics).distinctBy(::libraryStatisticsKey))
        retainPages && previous != null -> page.copy(next_cursor = if (removed > 0) movedCursor else previous.next_cursor,
            query = previous.query,
            cards = (page.cards + previous.cards).distinctBy(::cardLookupKey),
            statistics = (page.statistics + previous.statistics).distinctBy(::libraryStatisticsKey))
        else -> page
    }
    updateState { next -> next.copy(libraryQuery = page.query.copy(offset = 0), libraryPage = combined, libraryPageError = null) }
    if (retainPages && removed > 0 && movedCursor == null) previous?.let { loadLibraryPage(it.query, append = true) }
}

private fun String.movedBack(rows: Int): String? = toLongOrNull()?.let { (it - rows).coerceAtLeast(0L).toString() }
