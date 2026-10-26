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
    private var lastCheck: TimeMark? = null
    private var builtAt: TimeMark? = null
    private var lastInput = time.markNow()
    private var baseline: Revisions? = null

    fun foreground(value: Boolean) { foreground = value }
    fun active() = foreground
    fun input() { lastInput = time.markNow() }
    /** The server has no revisions endpoint, or this is a local connection. */
    fun unsupported() { supported = false }
    /** A check was requested or has just finished; event checks within [EVENT_GAP] of either coalesce. */
    fun checked() { lastCheck = time.markNow() }

    /** A fresh Home is on screen. [revisions] were read before it was built. */
    fun homeBuilt(revisions: Revisions?) {
        builtAt = time.markNow()
        revisions?.let(::adopt)
    }
    fun adopt(revisions: Revisions) { baseline = revisions }

    /** A new session or profile: nothing observed so far applies to it. */
    fun reset() {
        supported = true; lastCheck = null; builtAt = null; baseline = null
        lastInput = time.markNow()
    }

    /**
     * Every foreground return and screen entry checks, unless a check was
     * requested or finished within [EVENT_GAP] (the start and resume callbacks
     * of one return). Home polls every [POLL].
     */
    fun checkDue(polling: Boolean): Boolean {
        if (!foreground || !supported) return false
        val since = lastCheck?.elapsedNow() ?: return true
        return since >= if (polling) POLL else EVENT_GAP
    }

    /** Without a baseline the shown data cannot be proven current, so any observation is a change. */
    fun changed(observed: Revisions): Boolean = baseline != observed
    fun libraryChanged(observed: Revisions): Boolean = baseline?.let { it["library"] != observed["library"] } ?: true

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
