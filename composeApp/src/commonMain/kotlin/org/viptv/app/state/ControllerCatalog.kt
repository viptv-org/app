package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.Card

fun ApplicationController.refresh() = run {
    progressJob?.join()
    val profile=mutable.value.selectedProfile
    val result=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    accept(result)
    if(profile!=null) local?.let { mutable.value=mutable.value.copy(home=it.refreshHome(profile)) }
}

fun ApplicationController.editSearch(edit: (String) -> String) = search(edit(mutable.value.searchTerm))

fun ApplicationController.search(term: String) {
    mutable.value = mutable.value.copy(searchTerm = term)
    run {
    delay(300)
    val cards = if (term.isBlank()) emptyList() else local?.search(requireNotNull(mutable.value.selectedProfile), term)?.cards ?: requireNotNull(connection).search(term).cards
    mutable.value = mutable.value.copy(results = cards)
    }
}

fun ApplicationController.open(card: Card) {
    beginDetails()
    run { mutable.value = mutable.value.copy(detail = local?.detail(requireNotNull(mutable.value.selectedProfile), card) ?: requireNotNull(connection).detail(card)) }
}
/** Resolve provider metadata before source discovery, including the native identity evidence cache. */
fun ApplicationController.playTitle(card: Card) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val detail = local?.detail(profile, card) ?: requireNotNull(connection).detail(card)
    mutable.value = mutable.value.copy(detail = detail)
    val target = when {
        card.identity.episode != null -> detail.episodes.find { it.identity.kind == card.identity.kind && it.identity.id == card.identity.id } ?: card
        detail.item.identity.kind == "series" -> detail.episodes.firstOrNull { (it.identity.episode?.season ?: 1L) > 0L } ?: throw LocalFailure("Choose an episode from this title’s details.")
        else -> detail.item
    }
    chooseSource(target)
}

/** Resolve a linked identity through the server; presentation uses only its returned card. */

fun ApplicationController.openRemoteIdentity(kind: String, id: String) {
    beginDetails()
    run { mutable.value = mutable.value.copy(detail = requireNotNull(connection).detail(kind, id)) }
}

fun ApplicationController.chooseSource(card: Card) {
    releaseRemotePlayback()
    val origin = if (mutable.value.detail != null) Screen.Details else mutable.value.screen
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceReturnScreen = origin, sourceTitle = card, sources = emptyList(), findingSources = true)
    run {
        val operation = generation
        try {
            if (mutable.value.preferences == null) loadPreferences()
            val publish: (List<org.viptv.engine.wire.SourceCandidate>) -> Unit = { partial ->
                if (generation == operation) mutable.value = mutable.value.copy(sources = partial)
            }
            val sources = local?.sources(requireNotNull(mutable.value.selectedProfile), card, publish)
                ?: requireNotNull(connection).sources(card, publish)
            val profile = requireNotNull(mutable.value.selectedProfile)
            val ranked = local?.rankSources(profile, sources) ?: requireNotNull(connection).rankSources(profile, sources)
            mutable.value = mutable.value.copy(sources = ranked)
            if (mutable.value.preferences?.preferences?.source_selection == "best" && ranked.isNotEmpty()) playSource(ranked.first())
        } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
    }
}

private fun ApplicationController.beginDetails() {
    val previous = mutable.value
    val origin = previous.screen.takeUnless { it in listOf(Screen.Details, Screen.Sources, Screen.Player) }
        ?: previous.detailReturnScreen
    val history = if (previous.screen == Screen.Details) previous.detailHistory + listOfNotNull(previous.detail) else emptyList()
    mutable.value = previous.copy(detailReturnScreen = origin, detailHistory = history,
        screen = Screen.Details, detail = null)
}
