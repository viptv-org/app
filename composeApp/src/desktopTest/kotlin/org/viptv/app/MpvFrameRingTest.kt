package org.viptv.app

import org.viptv.app.player.mpv.MpvFrameRing
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.*

class MpvFrameRingTest {
    @Test fun nothingToAcquireBeforeTheFirstPublish() {
        assertFalse(MpvFrameRing().acquire())
    }

    @Test fun acquireReturnsTheNewestPublishedSlotAndDropsTheSupersededOne() {
        val ring = MpvFrameRing()
        val first = ring.back
        ring.publish()
        val second = ring.back
        ring.publish()
        assertNotEquals(first, second)
        assertTrue(ring.acquire())
        assertEquals(second, ring.front)
        assertFalse(ring.acquire())
    }

    @Test fun theProducerNeverWritesTheSlotOnScreen() {
        val ring = MpvFrameRing()
        repeat(1000) { i ->
            ring.publish()
            if (i % 3 == 0) ring.acquire()
            assertNotEquals(ring.back, ring.front)
            assertTrue(ring.back in 0..2 && ring.front in 0..2)
        }
    }

    @Test fun concurrentFramesArriveInOrderAndAreNeverOverwrittenOnScreen() {
        val ring = MpvFrameRing()
        val slots = LongArray(3)
        val done = AtomicBoolean(false)
        val producer = thread {
            for (frame in 1L..200_000L) { slots[ring.back] = frame; ring.publish() }
            done.set(true)
        }
        var last = 0L
        while (true) {
            val finished = done.get()
            if (ring.acquire()) {
                val frame = slots[ring.front]
                repeat(50) { Thread.onSpinWait() }
                assertEquals(frame, slots[ring.front])
                assertTrue(frame > last, "frame $frame after $last")
                last = frame
            } else if (finished) break
        }
        producer.join()
        assertEquals(200_000L, last)
    }
}
