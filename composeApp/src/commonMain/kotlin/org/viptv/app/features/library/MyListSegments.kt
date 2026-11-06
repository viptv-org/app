package org.viptv.app.features.library

import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.engine.wire.Card

// copy.md §Names — Continue Watching is the second segment of My List, beside the saved titles.
internal enum class MyListSegment(val label: String) { Saved("My List"), Continue("Continue Watching") }

internal val MyListSegmentLabels = listOf(MyListSegment.Saved.label, MyListSegment.Continue.label)

internal fun segmentIndex(segment: MyListSegment) = MyListSegment.entries.indexOf(segment)

// The second segment's own reads: it pages, retries, plays its own cards and removes from its own list.
internal data class ContinueWatchingSection(
    val page: ContinueWatchingPage,
    val play: (Card) -> Unit,
    val more: (() -> Unit)?,
    val retry: () -> Unit,
    val remove: ((Card) -> Unit)?,
    val home: () -> Unit,
)
