package org.viptv.app.state

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

typealias Revisions = Map<String, String>

class Freshness(private val time: TimeSource = TimeSource.Monotonic, private val idleAfter: Duration = IDLE) {
    private var foreground = true
    private var supported = true
    private var requested: TimeMark? = null
    private var finished: TimeMark? = null
    private var builtAt: TimeMark? = null
    private var lastInput = time.markNow()
    private var baseline: Revisions? = null

    fun foreground(value: Boolean) { foreground = value; if (!value) requested = null }
    fun active() = foreground
    fun input() { lastInput = time.markNow() }
    fun unsupported() { supported = false }
    fun checkRequested() { requested = time.markNow() }
    fun checkFinished() { finished = time.markNow(); requested = null }

    fun homeBuilt(revisions: Revisions?) {
        builtAt = time.markNow()
        revisions?.let(::adopt)
    }
    fun adopt(revisions: Revisions) { baseline = revisions }

    fun reset() {
        supported = true; requested = null; finished = null; builtAt = null; baseline = null
        lastInput = time.markNow()
    }

    fun checkDue(polling: Boolean): Boolean {
        if (!foreground || !supported) return false
        val since = listOfNotNull(requested, finished).minOfOrNull { it.elapsedNow() } ?: return true
        return since >= if (polling) POLL else EVENT_GAP
    }

    fun changed(observed: Revisions): Boolean = baseline != observed
    fun libraryChanged(observed: Revisions): Boolean = moved(observed, "library")
    fun addonsChanged(observed: Revisions): Boolean = moved(observed, "addons")
    private fun moved(observed: Revisions, token: String) = baseline?.let { it[token] != observed[token] } ?: true

    fun homeStale(): Boolean = builtAt?.let { it.elapsedNow() >= HOME_MAX_AGE } ?: false
    fun idle(): Boolean = lastInput.elapsedNow() >= idleAfter
    fun untilIdle(): Duration = idleAfter - lastInput.elapsedNow()

    companion object {
        val EVENT_GAP = 2.seconds
        val POLL = 15.seconds
        val HOME_MAX_AGE = 5.minutes
        val IDLE = 20.seconds
        val TICK = 5.seconds
    }
}
