package org.viptv.app.state

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.viptv.app.connection.RankedSources
import org.viptv.engine.wire.SourceCandidate

internal class ProgressiveSourceRanking(
    scope: CoroutineScope,
    private val rank: suspend (List<SourceCandidate>) -> RankedSources,
    private val show: (RankedSources) -> Unit,
    private val spacingMillis: Long = 300,
    private val onMatch: (RankedSources) -> Unit = {},
) {
    private val arrivals = Channel<List<SourceCandidate>>(Channel.CONFLATED)
    private var submitted: List<String> = emptyList()
    private var shown = RankedSources(emptyList())
    private var failed = false

    private val worker = scope.launch {
        var matched = false
        for (rows in arrivals) {
            if (failed) { appendUnranked(rows); continue }
            val spacing = launch { delay(spacingMillis) }
            try {
                shown = rank(rows)
                show(shown)
                if (!matched && shown.bestMatchFullyMatches) {
                    matched = true
                    onMatch(shown)
                }
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

    fun arrived(rows: List<SourceCandidate>) {
        val ids = rows.map { it.id }
        if (ids == submitted) return
        submitted = ids
        arrivals.trySend(rows)
    }

    suspend fun finish(): RankedSources? {
        arrivals.close()
        worker.join()
        return shown.takeUnless { failed }
    }
}
