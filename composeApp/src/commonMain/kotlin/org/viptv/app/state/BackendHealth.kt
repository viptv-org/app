package org.viptv.app.state

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class BackendHealth(private val time: TimeSource = TimeSource.Monotonic) {
    private var failures = 0
    private var outage: TimeMark? = null
    private var retryAt: TimeMark? = null

    fun unreachable(): Boolean {
        val started = outage == null
        if (started) outage = time.markNow()
        failures++
        retryAt = time.markNow() + retryDelay(failures)
        return started
    }

    fun failed() {
        failures++
        retryAt = time.markNow() + retryDelay(failures)
    }

    fun answered() {
        failures = 0
        outage = null
        retryAt = null
    }

    fun offline(): Boolean = outage != null

    fun retryDue(): Boolean = retryAt?.hasPassedNow() ?: true

    val retrySeconds: Int get() = retryDelay(failures).inWholeSeconds.toInt()

    companion object {
        val FIRST = 10.seconds
        val MAX = 60.seconds

        internal fun retryDelay(failures: Int): Duration = minOf(FIRST * (1 shl (failures - 1).coerceIn(0, 3)), MAX)
    }
}
