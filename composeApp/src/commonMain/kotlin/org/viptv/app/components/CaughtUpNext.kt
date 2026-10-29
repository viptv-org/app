package org.viptv.app.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationEntry
import org.viptv.engine.wire.NextEpisode

@Immutable
class NextEpisodes(private val byKey: Map<String, NextEpisode>) {
    operator fun get(card: Card): NextEpisode? =
        if (byKey.isEmpty()) null else byKey[cardLookupKey(card)]

    fun announced(entries: List<ContinuationEntry>): NextEpisodes {
        if (entries.isEmpty()) return this
        val page = entries.associate { cardLookupKey(it.card) to it.next_episode }
        return NextEpisodes((byKey + page).mapNotNull { (key, next) -> next?.let { key to it } }.toMap())
    }

    companion object {
        val Empty = NextEpisodes(emptyMap())
    }
}

internal val LocalNextEpisodes = compositionLocalOf { NextEpisodes.Empty }
