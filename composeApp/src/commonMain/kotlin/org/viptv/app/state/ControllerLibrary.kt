package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.*

fun ApplicationController.toggleFavorite(card: Card) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    libraryHydration?.cancel()
    libraryHydration?.join()
    if (mutable.value.favoriteKeys == null || card.metadata_origin != null && mutable.value.libraryMembership == null) loadLibrary()
    // Respect the action shown to the user. A previously unverified catalog
    // alias becoming known must not turn an Add action into a removal.
    val saved = mutable.value.isSaved(card)
    val favorite = if (saved) card.parent_identity?.let { card.copy(identity = it, parent_identity = null) } ?: card
        else if (card.metadata_origin != null || card.parent_identity != null || card.identity.episode != null)
            (local?.detail(profile, card) ?: requireNotNull(connection).detail(card)).item else card
    if (saved) local?.removeFavorite(profile, favorite) ?: requireNotNull(connection).removeFavorite(profile, favorite)
    else local?.saveFavorite(profile, favorite) ?: requireNotNull(connection).saveFavorite(profile, favorite)
    loadLibrary()
    if (!saved) adoptFavoriteAssertions(favorite)
    if (mutable.value.screen == Screen.Library) loadLibraryPage()
}

internal suspend fun ApplicationController.loadLibrary() {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val device = local
    if (device != null) {
        val membership = device.libraryMembership(profile)
        updateLibrary(device.library(profile), membership = membership)
    }
    else {
        val remote = requireNotNull(connection)
        val membership = remote.libraryMembership(profile)
        updateLibrary(remote.library(profile), membership = membership)
    }
}

fun ApplicationController.clearHistory() = run {
    val profile=requireNotNull(mutable.value.selectedProfile)
    if (local != null) requireNotNull(local).clearHistory(profile) else requireNotNull(connection).clearHistory(profile)
    mutable.value=mutable.value.copy(history=emptyList())
    val updated=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(home=updated.home)
}

internal fun ApplicationController.updateLibrary(cards: List<Card>, identities: List<MediaIdentity> = cards.map { it.identity },
    membership: LibraryMembershipSnapshot? = null) {
    val ordered = cards.sortedWith(compareByDescending<Card> { it.added_at_millis ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() })
    val home = mutable.value.home?.let { snapshot ->
        val existing = snapshot.rows.find { it.id == "favorites" && it.provider_id == "library" }
        val row = existing?.copy(cards = ordered.take(12), state = if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, next_cursor = null)
            ?: HomeRow(id = "favorites", title = "My List", provider_id = "library", state = if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, cards = ordered.take(12))
        val rows = if (existing != null) snapshot.rows.map { if (it === existing) row else it }
            else snapshot.rows.toMutableList().apply { add(indexOfFirst { it.id != "continue" }.takeIf { it >= 0 } ?: size, row) }
        snapshot.copy(rows = rows)
    }
    mutable.value = mutable.value.copy(library = ordered, home = home, favoriteKeys = (membership?.identities ?: identities).map { it.kind to it.id }.toSet(), libraryMembership = membership)
}

/** Upgrade current previews from the host response, never saved/history selectors. */
private fun ApplicationController.adoptFavoriteAssertions(authoritative: Card) {
    if (authoritative.metadata_origin == null) return
    fun adopt(card: Card): Card = if (card.metadata_origin == authoritative.metadata_origin &&
        card.provider_id == authoritative.provider_id && card.identity.kind == authoritative.identity.kind &&
        card.identity.id == authoritative.identity.id && card.identity.episode == authoritative.identity.episode &&
        card.identity.edition == authoritative.identity.edition && card.identity.ids.all { it in authoritative.identity.ids })
        card.copy(identity = authoritative.identity) else card
    val state = mutable.value
    mutable.value = state.copy(
        results = state.results.map(::adopt), searchRows = state.searchRows.map { it.copy(cards = it.cards.map(::adopt)) },
        browseCards = state.browseCards.map(::adopt), detail = state.detail?.let { it.copy(item = adopt(it.item)) },
        home = state.home?.let { home -> home.copy(hero = home.hero?.let(::adopt), featured = home.featured.map(::adopt),
            rows = home.rows.map { row -> if (row.provider_id == "library" || row.id in setOf("favorites", "continue")) row
                else row.copy(cards = row.cards.map(::adopt)) }) })
}
