package org.viptv.app.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.ContinuationEntry
import org.viptv.engine.wire.NextEpisode

/**
 * The next episode each caught-up Continue Watching card is waiting for (app#132), keyed by
 * [cardLookupKey]. The continuation page carries it per entry ([ContinuationEntry.next_episode]),
 * while every board draws a plain [Card]; the shell publishes these facts once, as it does the
 * card actions (DUI-04), so Home's row, the See all page and TV's shelf and grid label the same
 * card the same way without a map threaded through every row and screen.
 */
@Immutable
class NextEpisodes(private val byKey: Map<String, NextEpisode>) {
    /** The episode [card] waits for, when the queue announced one for it. */
    operator fun get(card: Card): NextEpisode? =
        if (byKey.isEmpty()) null else byKey[cardLookupKey(card)]

    /**
     * These facts with [entries]' announcements merged in: an entry that announces nothing clears
     * what it announced before (its next episode is out now, or the show has ended), and entries
     * this page did not hold keep theirs.
     */
    fun announced(entries: List<ContinuationEntry>): NextEpisodes {
        if (entries.isEmpty()) return this
        val page = entries.associate { cardLookupKey(it.card) to it.next_episode }
        return NextEpisodes((byKey + page).mapNotNull { (key, next) -> next?.let { key to it } }.toMap())
    }

    companion object {
        /** A profile that knows nothing yet: a page's announcements replace these. */
        val Empty = NextEpisodes(emptyMap())
    }
}

internal val LocalNextEpisodes = compositionLocalOf { NextEpisodes.Empty }
