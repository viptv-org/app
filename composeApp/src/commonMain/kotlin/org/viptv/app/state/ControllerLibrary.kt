package org.viptv.app.state

import org.viptv.app.components.isWatched
import org.viptv.app.connection.*
import org.viptv.app.features.home.CONTINUE_ROW
import org.viptv.app.features.home.MY_LIST_ROW
import org.viptv.engine.wire.*

fun ApplicationController.toggleFavorite(card: Card) {
    val saved = mutable.value.isSaved(card)
    mutate(saveTarget(card), optimistic = { it.withSavedOverride(card, !saved) }, revert = { it.withSavedOverride(card, null) },
        ownsControllerJob = true) {
        val profile = requireNotNull(mutable.value.selectedProfile)
        libraryHydration?.cancel()
        libraryHydration?.join()
        if (mutable.value.favoriteKeys == null || card.metadata_origin != null && mutable.value.libraryMembership == null) loadLibrary()
        // A catalog alias that becomes known must not turn a shown Add action into a removal. The optimistic
        // override is this press's own prediction, so the decision reads the host's answer instead of it.
        val wasSaved = mutable.value.isSavedByHost(card)
        val favorite = if (wasSaved) card.parent_identity?.let { card.copy(identity = it, parent_identity = null) } ?: card
            else if (card.metadata_origin != null || card.parent_identity != null || card.identity.episode != null)
                (local?.detail(profile, card) ?: requireNotNull(connection).detail(card)).item else card
        if (wasSaved) local?.removeFavorite(profile, favorite) ?: requireNotNull(connection).removeFavorite(profile, favorite)
        else local?.saveFavorite(profile, favorite) ?: requireNotNull(connection).saveFavorite(profile, favorite)
        loadLibrary()
        if (!wasSaved) adoptFavoriteAssertions(favorite)
        reloadMyListAfterWrite(unsaved = if (wasSaved) card else null)
        updateState { it.withSavedOverride(card, null) }
    }
}

// The control the viewer pressed flips in the same frame; the write then settles it.
internal fun saveTarget(card: Card) = MutationTarget(MutationKind.MyList, cardLookupKey(card))

internal fun ApplicationState.withSavedOverride(card: Card, saved: Boolean?): ApplicationState {
    val key = cardLookupKey(card)
    return copy(savedOverrides = if (saved == null) savedOverrides - key else savedOverrides + (key to saved))
}

// [ApplicationState.isSaved] without the optimistic override the press just published, so a write decides
// from the host's answer rather than from its own prediction.
internal fun ApplicationState.isSavedByHost(card: Card): Boolean {
    libraryMembership?.let { return it.containsCard(card) }
    // Only an exact saved lookup may match a card with a metadata origin; older hosts send no membership.
    if (card.metadata_origin != null) return savedCardsContain(library, card)
    val identity = card.parent_identity ?: card.identity
    return favoriteKeys?.contains(identity.kind to identity.id) ?: savedCardsContain(library, card)
}

// The card leaves at once; the re-read then agrees with the host on membership and the page.
fun ApplicationController.removeFromMyList(card: Card) {
    val before = mutable.value
    mutate(saveTarget(card),
        optimistic = { it.withoutSavedCard(card).withSavedOverride(card, false) },
        revert = { it.copy(library = before.library, libraryPage = before.libraryPage, home = before.home,
            favoriteKeys = before.favoriteKeys, savedOverrides = it.savedOverrides - cardLookupKey(card)) },
        ownsControllerJob = true) {
        val profile = requireNotNull(mutable.value.selectedProfile)
        libraryHydration?.cancel()
        libraryHydration?.join()
        if (mutable.value.favoriteKeys == null || card.metadata_origin != null && mutable.value.libraryMembership == null) loadLibrary()
        // A catalog alias that became known must not leave the removal pointing at a title nobody saved.
        val favorite = card.parent_identity?.let { card.copy(identity = it, parent_identity = null) } ?: card
        local?.removeFavorite(profile, favorite) ?: requireNotNull(connection).removeFavorite(profile, favorite)
        loadLibrary()
        reloadMyListAfterWrite(unsaved = card)
        updateState { it.withSavedOverride(card, null) }
    }
}

internal fun ApplicationState.withoutSavedCard(card: Card): ApplicationState {
    val key = cardLookupKey(card)
    fun List<Card>.without(): List<Card> = filterNot { cardLookupKey(it) == key }
    return copy(
        library = library.without(),
        libraryPage = libraryPage?.let { page -> page.copy(cards = page.cards.without()) },
        home = home?.let { snapshot -> snapshot.copy(rows = snapshot.rows.map { row ->
            if (row.id != MY_LIST_ROW || row.provider_id != "library") row
            else row.copy(cards = row.cards.without()).let { shown ->
                if (shown.cards.isEmpty()) shown.copy(state = RowState.EMPTY) else shown
            } }) },
        favoriteKeys = favoriteKeys?.filterNot { it == (card.identity.kind to card.identity.id) }?.toSet(),
    )
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
    updateState { next -> next.copy(history=emptyList()) }
    val updated=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    updateState { next -> next.copy(home=updated.home) }
}

fun ApplicationController.correctHistory(display: HistoryDisplay, correction: HistoryCorrection) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val remote = connection ?: return@run
    val corrected = remote.correctProgress(profile, display, correction)
    updateState { state ->
        val key = cardLookupKey(display.entry.card)
        state.copy(history = state.history.map { existing ->
            if (cardLookupKey(existing.entry.card) != key) existing
            else display.copy(position = corrected.position, duration = corrected.duration, watched = corrected.watched,
                resumeActive = corrected.resumeActive, completionOnly = corrected.completionOnly, watchDateKnown = corrected.watchDateKnown)
        })
    }
}

// A title marked from its own menu has no history row to correct, so the page re-reads instead.
fun ApplicationController.toggleWatched(card: Card) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val remote = connection ?: throw LocalFailure("Connect to your account to mark this title watched.")
    val display = HistoryDisplay(HistoryEntry(card, 0L), "")
    remote.correctProgress(profile, display, if (card.isWatched()) HistoryCorrection.Unwatched else HistoryCorrection.Watched)
    requestQuiet(QuietWork.Refresh, detail = true)
}

internal fun ApplicationController.updateLibrary(cards: List<Card>, identities: List<MediaIdentity> = cards.map { it.identity },
    membership: LibraryMembershipSnapshot? = null) {
    val ordered = cards.sortedWith(compareByDescending<Card> { it.added_at_millis ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() })
    updateState { next ->
        val home = next.home?.let { snapshot ->
            val existing = snapshot.rows.find { it.id == MY_LIST_ROW && it.provider_id == "library" }
            val row = existing?.copy(cards = ordered.take(12), state = if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, next_cursor = null)
                ?: HomeRow(id = MY_LIST_ROW, title = "My List", provider_id = "library", state = if (ordered.isEmpty()) RowState.EMPTY else RowState.READY, cards = ordered.take(12))
            val rows = if (existing != null) snapshot.rows.map { if (it === existing) row else it }
                else snapshot.rows.toMutableList().apply { add(indexOfFirst { it.id != CONTINUE_ROW }.takeIf { it >= 0 } ?: size, row) }
            snapshot.copy(rows = rows)
        }
        next.copy(library = ordered, home = home, favoriteKeys = (membership?.identities ?: identities).map { it.kind to it.id }.toSet(), libraryMembership = membership)
    }
}

private fun ApplicationController.adoptFavoriteAssertions(authoritative: Card) {
    if (authoritative.metadata_origin == null) return
    fun adopt(card: Card): Card = if (card.metadata_origin == authoritative.metadata_origin &&
        card.provider_id == authoritative.provider_id && card.identity.kind == authoritative.identity.kind &&
        card.identity.id == authoritative.identity.id && card.identity.episode == authoritative.identity.episode &&
        card.identity.edition == authoritative.identity.edition && card.identity.ids.all { it in authoritative.identity.ids })
        card.copy(identity = authoritative.identity) else card
    updateState { state -> state.copy(
        results = state.results.map(::adopt), searchRows = state.searchRows.map { it.copy(cards = it.cards.map(::adopt)) },
        browseCards = state.browseCards.map(::adopt), detail = state.detail?.let { it.copy(item = adopt(it.item)) },
        home = state.home?.let { home -> home.copy(hero = home.hero?.let(::adopt), featured = home.featured.map(::adopt),
            rows = home.rows.map { row -> if (row.provider_id == "library" || row.id in setOf(MY_LIST_ROW, CONTINUE_ROW)) row
                else row.copy(cards = row.cards.map(::adopt)) }) }) }
}
