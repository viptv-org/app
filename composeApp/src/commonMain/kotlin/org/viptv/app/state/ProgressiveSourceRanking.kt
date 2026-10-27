package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.viptv.app.connection.RankedSources
import org.viptv.engine.wire.SourceCandidate

/**
 * Ranks a discovery's rows as providers answer (SRC-BEST-MATCH-001, Progressive ranking).
 *
 * Each [arrived] call carries every row found so far. Rows are shown only at their ranked
 * positions, never in arrival order: the first arrival is ranked at once, later ones at
 * most once per [spacingMillis], with one request in flight and only the latest rows
 * ranked. When a ranking request fails, the rows already shown keep their order, later
 * rows append in arrival order without groups, and this discovery is not ranked again.
 */
internal class ProgressiveSourceRanking(
    scope: CoroutineScope,
    private val rank: suspend (List<SourceCandidate>) -> RankedSources,
    private val show: (RankedSources) -> Unit,
    private val spacingMillis: Long = 300,
) {
    private val arrivals = Channel<List<SourceCandidate>>(Channel.CONFLATED)
    private var submitted: List<String> = emptyList()
    private var shown = RankedSources(emptyList())
    private var failed = false

    private val worker = scope.launch {
        for (rows in arrivals) {
            if (failed) { appendUnranked(rows); continue }
            val spacing = launch { delay(spacingMillis) }
            try {
                shown = rank(rows)
                show(shown)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                failed = true
                spacing.cancel()
                appendUnranked(rows)
            }
            spacing.join()
        }
    }

    private fun appendUnranked(rows: List<SourceCandidate>) {
        val known = shown.rows.mapTo(HashSet()) { it.id }
        shown = RankedSources(shown.rows + rows.filter { it.id !in known })
        show(shown)
    }

    /** Every row found so far, in arrival order. */
    fun arrived(rows: List<SourceCandidate>) {
        val ids = rows.map { it.id }
        if (ids == submitted) return
        submitted = ids
        arrivals.trySend(rows)
    }

    /** Ranks the last arrivals, then the final ranking, or null when ranking failed. */
    suspend fun finish(): RankedSources? {
        arrivals.close()
        worker.join()
        return shown.takeUnless { failed }
    }
}
