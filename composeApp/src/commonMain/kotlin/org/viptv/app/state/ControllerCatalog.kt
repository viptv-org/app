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
    mutable.value = mutable.value.copy(screen = Screen.Details, detail = null)
    run { mutable.value = mutable.value.copy(detail = local?.detail(requireNotNull(mutable.value.selectedProfile), card) ?: requireNotNull(connection).detail(card)) }
}
/** Resolve a linked identity through the server; presentation uses only its returned card. */

fun ApplicationController.openRemoteIdentity(kind: String, id: String) {
    mutable.value = mutable.value.copy(screen = Screen.Details, detail = null)
    run { mutable.value = mutable.value.copy(detail = requireNotNull(connection).detail(kind, id)) }
}

fun ApplicationController.chooseSource(card: Card) {
    releaseRemotePlayback()
    mutable.value = mutable.value.copy(screen = Screen.Sources, sourceTitle = card, sources = emptyList(), findingSources = true)
    run {
        val operation = generation
        try {
            val sources = local?.sources(requireNotNull(mutable.value.selectedProfile), card)
                ?: requireNotNull(connection).sources(card) { partial ->
                    if (generation == operation) mutable.value = mutable.value.copy(sources = partial)
                }
            mutable.value = mutable.value.copy(sources = sources)
        } finally { if (generation == operation) mutable.value = mutable.value.copy(findingSources = false) }
    }
}
