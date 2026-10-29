package org.viptv.app.state

import kotlinx.coroutines.*
import org.viptv.app.connection.PlaybackEvent
import org.viptv.app.player.PlayerFact

internal class PlaybackTracking(
    private val scope: CoroutineScope,
    private val emit: suspend (PlaybackEvent, Long, Long?, Boolean) -> Unit,
    private val failed: () -> Unit,
) {
    private var previous: Job? = null
    private var last: PlaybackEvent? = null
    private var retired = false

    fun transition(event: PlaybackEvent, progress: PlayerFact.Progress?, ended: Boolean = false): Job? {
        if (retired || last == event) return previous
        if (event == PlaybackEvent.Stop) retired = true
        last = event
        val prior = previous
        return scope.launch(start = if (event == PlaybackEvent.Stop) CoroutineStart.UNDISPATCHED else CoroutineStart.DEFAULT) {
            withContext(if (event == PlaybackEvent.Stop) NonCancellable else currentCoroutineContext()) {
            prior?.join()
            try {
                withTimeout(5_000) { emit(event, progress?.positionMillis ?: 0, progress?.durationMillis, ended) }
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
                failed()
            } catch (_: Exception) { failed() }
            }
        }.also { previous = it }
    }
}
