package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.*

fun ApplicationController.toggleFavorite(card: Card) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    libraryHydration?.cancel()
    libraryHydration?.join()
    loadLibrary()
    val favorite = if (card.parent_identity != null || card.identity.episode != null)
        (local?.detail(profile, card) ?: requireNotNull(connection).detail(card)).item else card
    val saved = mutable.value.library.any { it.identity.kind == favorite.identity.kind && it.identity.id == favorite.identity.id }
    if (saved) local?.removeFavorite(profile, favorite) ?: requireNotNull(connection).removeFavorite(profile, favorite)
    else local?.saveFavorite(profile, favorite) ?: requireNotNull(connection).saveFavorite(profile, favorite)
    loadLibrary()
}

internal suspend fun ApplicationController.loadLibrary() {
    val profile = requireNotNull(mutable.value.selectedProfile)
    updateLibrary(local?.library(profile) ?: requireNotNull(connection).library(profile))
}

fun ApplicationController.clearHistory() = run {
    val profile=requireNotNull(mutable.value.selectedProfile)
    if (local != null) requireNotNull(local).clearHistory(profile) else requireNotNull(connection).clearHistory(profile)
    mutable.value=mutable.value.copy(history=emptyList())
    val updated=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(home=updated.home)
}

internal fun ApplicationController.updateLibrary(cards: List<Card>) {
    val ordered = cards.sortedWith(compareByDescending<Card> { it.added_at_millis ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() })
    val home = mutable.value.home?.let { snapshot ->
        val existing = snapshot.rows.find { it.id == "favorites" && it.provider_id == "library" }
        val row = existing?.copy(cards = ordered, state = if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, next_cursor = null)
            ?: HomeRow("favorites", "My List", "library", if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, ordered)
        val rows = if (existing != null) snapshot.rows.map { if (it === existing) row else it }
            else snapshot.rows.toMutableList().apply { add(indexOfFirst { it.id != "continue" }.takeIf { it >= 0 } ?: size, row) }
        snapshot.copy(rows = rows)
    }
    mutable.value = mutable.value.copy(library = ordered, home = home)
}
