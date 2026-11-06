package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import org.viptv.app.connection.RankedSources
import org.viptv.engine.wire.SourceCandidate

internal const val BEST_MATCH_GRACE_MILLIS = 1_500L
internal const val BEST_MATCH_CAP_MILLIS = 5_000L

internal data class BestMatchStartTarget(val ranked: RankedSources, val source: SourceCandidate)

// Times a Best match start: 1.5 s after the first row that meets every preference, otherwise the best row 5 s
// after the first one, and at the end of discovery when that comes first.
internal class BestMatchStart(
    private val graceMillis: Long = BEST_MATCH_GRACE_MILLIS,
    private val capMillis: Long = BEST_MATCH_CAP_MILLIS,
) {
    suspend fun await(rankings: ReceiveChannel<RankedSources>, stopped: () -> Boolean = { false }): BestMatchStartTarget? =
        coroutineScope {
            var best: BestMatchStartTarget? = null
            var fullMatch: BestMatchStartTarget? = null
            var decision: BestMatchStartTarget? = null
            var decided = false
            var cap: Job? = null
            var grace: Job? = null
            try {
                while (!decided) {
                    val event = select<Event> {
                        rankings.onReceiveCatching { received -> received.getOrNull()?.let(Event::Ranked) ?: Event.Ended }
                        cap?.let { timer -> timer.onJoin { Event.Cap } }
                        grace?.let { timer -> timer.onJoin { Event.Grace } }
                    }
                    if (event !is Event.Ranked && stopped()) { decided = true; continue }
                    when (event) {
                        is Event.Ranked -> {
                            if (stopped()) { decided = true; continue }
                            val target = event.ranked.autoplayTarget() ?: continue
                            val candidate = BestMatchStartTarget(event.ranked, target)
                            best = candidate
                            if (event.ranked.bestMatchFullyMatches) {
                                fullMatch = candidate
                                if (grace == null) {
                                    cap?.cancel(); cap = null
                                    grace = launch { delay(graceMillis) }
                                }
                            } else if (cap == null && grace == null) {
                                cap = launch { delay(capMillis) }
                            }
                        }
                        Event.Grace -> { decision = fullMatch; decided = true }
                        Event.Cap -> { decision = best; decided = true }
                        Event.Ended -> { decision = fullMatch ?: best; decided = true }
                    }
                }
                if (stopped()) null else decision
            } finally {
                cap?.cancel()
                grace?.cancel()
            }
        }

    private sealed interface Event {
        data class Ranked(val ranked: RankedSources) : Event
        data object Grace : Event
        data object Cap : Event
        data object Ended : Event
    }
}