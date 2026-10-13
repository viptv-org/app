package org.viptv.app.state

import org.viptv.app.connection.*
import org.viptv.engine.wire.Card

fun ApplicationController.toggleFavorite(card: Card) = run {
    val profile = requireNotNull(mutable.value.selectedProfile)
    loadLibrary()
    val saved = mutable.value.library.any { it.identity.kind == card.identity.kind && it.identity.id == card.identity.id }
    if (saved) local?.removeFavorite(profile, card) ?: requireNotNull(connection).removeFavorite(profile, card)
    else local?.saveFavorite(profile, card) ?: requireNotNull(connection).saveFavorite(profile, card)
    mutable.value = mutable.value.copy(library = if (saved) mutable.value.library.filterNot { it.identity.kind == card.identity.kind && it.identity.id == card.identity.id } else listOf(card) + mutable.value.library)
}

internal suspend fun ApplicationController.loadLibrary() {
    val profile = requireNotNull(mutable.value.selectedProfile)
    mutable.value = mutable.value.copy(library = local?.library(profile) ?: requireNotNull(connection).library(profile))
}

fun ApplicationController.clearHistory() = run {
    val profile=requireNotNull(mutable.value.selectedProfile)
    if (local != null) requireNotNull(local).clearHistory(profile) else requireNotNull(connection).clearHistory(profile)
    mutable.value=mutable.value.copy(history=emptyList())
    val updated=local?.bootstrap(profile) ?: requireNotNull(connection).bootstrap()
    mutable.value=mutable.value.copy(home=updated.home)
}
