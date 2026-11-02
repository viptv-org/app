package org.viptv.app.player.mpv

import java.util.concurrent.atomic.AtomicInteger

// Triple buffer: the producer only writes [back], the consumer only reads [front], and the newest
// finished frame waits in the middle slot until the consumer swaps it in.
internal class MpvFrameRing {
    private val middle = AtomicInteger(1)
    var back = 2; private set
    var front = 0; private set

    fun publish() { back = middle.getAndSet(back or FRESH) and SLOT }

    fun acquire(): Boolean {
        if (middle.get() and FRESH == 0) return false
        front = middle.getAndSet(front) and SLOT
        return true
    }

    private companion object { const val SLOT = 3; const val FRESH = 4 }
}
