package org.viptv.app.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Retains the actual media timeline when only the Activity/view is recreated. */
@UnstableApi
internal class AndroidPlaybackModel : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var input: PlaybackInput? = null
    private var session: AndroidPlaybackSession? = null

    fun acquire(context: Context, input: PlaybackInput, observe: (PlayerFact) -> Unit): AndroidPlaybackSession {
        if (this.input !== input) {
            session?.close()
            this.input = input
            session = AndroidPlaybackSession(context.applicationContext, input, scope, observe)
        }
        return requireNotNull(session).also { it.observe = observe }
    }

    fun release(value: AndroidPlaybackSession) {
        if (session !== value) return
        value.close(); session = null; input = null
    }
    override fun onCleared() { session?.close(); session = null; scope.cancel() }
}
