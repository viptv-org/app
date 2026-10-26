package org.viptv.app.state

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Opaque per-domain change tokens for one profile. Only equality is meaningful. */
typealias Revisions = Map<String, String>

/**
 * When to look for server-side changes (HOME-ADDON-001, AND-041). The
 * controller reports what happened and performs the checks this allows.
 * Checks are one cheap revisions request. Home is rebuilt only when a token
 * moved, after playback, or when an aged Home meets an idle viewer.
 */
class Freshness(private val time: TimeSource = TimeSource.Monotonic) {
    private var foreground = true
    private var supported = true
    /** A check requested in this foreground spell that has not finished yet. */
    private var requested: TimeMark? = null
    private var finished: TimeMark? = null
    private var builtAt: TimeMark? = null
    private var lastInput = time.markNow()
    private var baseline: Revisions? = null

    /** Going behind forgets a requested check: it may read the tokens before a change made meanwhile. */
    fun foreground(value: Boolean) { foreground = value; if (!value) requested = null }
    fun active() = foreground
    fun input() { lastInput = time.markNow() }
    /** The server has no revisions endpoint, or this is a local connection. */
    fun unsupported() { supported = false }
    /** Marked when a check is asked for, so the resume right behind a start joins it. */
    fun checkRequested() { requested = time.markNow() }
    fun checkFinished() { finished = time.markNow(); requested = null }

    /** A fresh Home is on screen. [revisions] were read before it was built. */
    fun homeBuilt(revisions: Revisions?) {
        builtAt = time.markNow()
        revisions?.let(::adopt)
    }
    fun adopt(revisions: Revisions) { baseline = revisions }

    /** A new session or profile: nothing observed so far applies to it. */
    fun reset() {
        supported = true; requested = null; finished = null; builtAt = null; baseline = null
        lastInput = time.markNow()
    }

    /**
     * Every foreground return and screen entry checks, unless a check
     * finished, or was requested since the return, within [EVENT_GAP] (the
     * start and resume callbacks of one return). Home polls every [POLL].
     */
    fun checkDue(polling: Boolean): Boolean {
        if (!foreground || !supported) return false
        val since = listOfNotNull(requested, finished).minOfOrNull { it.elapsedNow() } ?: return true
        return since >= if (polling) POLL else EVENT_GAP
    }

    /** Without a baseline the shown data cannot be proven current, so any observation is a change. */
    fun changed(observed: Revisions): Boolean = baseline != observed
    fun libraryChanged(observed: Revisions): Boolean = moved(observed, "library")
    fun addonsChanged(observed: Revisions): Boolean = moved(observed, "addons")
    private fun moved(observed: Revisions, token: String) = baseline?.let { it[token] != observed[token] } ?: true

    /** Catalog contents, trending and next-episode availability age even when no token moves. */
    fun homeStale(): Boolean = builtAt?.let { it.elapsedNow() >= HOME_MAX_AGE } ?: false
    fun idle(): Boolean = lastInput.elapsedNow() >= IDLE

    companion object {
        val EVENT_GAP = 2.seconds
        val POLL = 15.seconds
        /** The server caches add-on catalogs for 300 s, so an earlier rebuild cannot show new content. */
        val HOME_MAX_AGE = 5.minutes
        val IDLE = 20.seconds
        val TICK = 5.seconds
    }
}
