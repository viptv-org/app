package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.LibraryQuery

fun ApplicationController.libraryQuery(query: LibraryQuery) {
    mutable.value = mutable.value.copy(libraryQuery = query.copy(offset = 0))
    run { loadLibraryPage(query.copy(offset = 0)) }
}
fun ApplicationController.libraryMore() = run {
    val offset = mutable.value.libraryPage?.next_cursor?.toLongOrNull() ?: return@run
    loadLibraryPage(mutable.value.libraryQuery.copy(offset = offset), append = true)
}
fun ApplicationController.refreshLibraryPage() = run { loadLibraryPage(refresh = true) }
internal suspend fun ApplicationController.loadLibraryPage(query: LibraryQuery = mutable.value.libraryQuery, append: Boolean = false, refresh: Boolean = false) {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val page = local?.libraryPage(profile, query) ?: requireNotNull(connection).libraryPage(profile, query)
    val previous = mutable.value.libraryPage
    val retainPages = refresh && previous != null && previous.cards.size > query.limit && page.next_cursor != null
    val combined = when {
        append && previous != null -> page.copy(
            cards = (previous.cards + page.cards).distinctBy(::cardLookupKey),
            statistics = (previous.statistics + page.statistics).distinctBy(::libraryStatisticsKey))
        retainPages && previous != null -> page.copy(next_cursor = previous.next_cursor,
            cards = (page.cards + previous.cards).distinctBy(::cardLookupKey),
            statistics = (page.statistics + previous.statistics).distinctBy(::libraryStatisticsKey))
        else -> page
    }
    mutable.value = mutable.value.copy(libraryQuery = page.query.copy(offset = 0), libraryPage = combined)
}
