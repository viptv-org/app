package org.viptv.app.player

/** Queued transport intent is confirmed only by matching SDK status observations. */
internal class PlaybackTransportGate {
    private var opened = false
    private var pendingPause: Boolean? = null

    fun request(paused: Boolean, observedStatus: String) {
        opened = true
        pendingPause = paused.takeUnless { observedStatus == if (paused) "Paused" else "Playing" }
    }

    fun accept(status: String): Boolean {
        if (status !in listOf("Playing", "Paused")) return true
        if (!opened) return false
        val observedPause = status == "Paused"
        if (pendingPause != null && observedPause != pendingPause) return false
        pendingPause = null
        return true
    }
}
