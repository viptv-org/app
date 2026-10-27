package org.viptv.app

/** The browser's session history as the web build drives it. Our entries carry an index. */
interface SessionHistory {
    /** `history.pushState`: a new entry after the current one; entries ahead of it are dropped. */
    fun push(index: Int)
    /** `history.replaceState` on the current entry. */
    fun replace(index: Int)
    /** `history.go`: the browser moves later and reports it as a popstate. */
    fun go(delta: Int)
}

/**
 * Makes the browser's Back and Forward buttons the app's own (app#20, app#45).
 *
 * The app keeps its own history ([org.viptv.app.state.ApplicationState.navBack], overlays,
 * Settings levels), so the browser only needs to know whether a step exists. This keeps one of our
 * entries behind the current one while the app [canBack], and one ahead while it [canForward]; a
 * press arrives as a popstate and becomes one [back] or [forward] step. On a root page there is
 * no step: browser Back leaves the site, as on any web page.
 */
class BrowserBackHistory(
    private val history: SessionHistory,
    private val canBack: () -> Boolean,
    private val canForward: () -> Boolean,
    private val back: () -> Unit,
    private val forward: () -> Unit,
) {
    /** Our index of the entry the browser shows, and of the last of our entries. */
    private var current = 0
    private var top = 0
    /** Moves we started with [SessionHistory.go] that have not been reported yet. */
    private var pending = 0

    fun start() { history.replace(0); settle() }

    /** The app's state changed: make the browser's buttons match what the app can do. */
    fun changed() = settle()

    /** A popstate: the browser now shows our entry [index], or an entry that isn't ours (null). */
    fun moved(index: Int?) {
        if (pending > 0) { pending--; if (index != null) current = index; settle(); return }
        // An entry from before this page load: keep going back, out of the site.
        if (index == null) { history.go(-1); return }
        val steps = index - current
        current = index
        when {
            // Back on a root page: the entry behind is ours but the app has no step; leave the site.
            steps < 0 && !canBack() -> { history.go(-(index + 1)); return }
            steps < 0 -> repeat(-steps) { back() }
            steps > 0 -> repeat(steps) { forward() }
        }
        settle()
    }

    private fun settle() {
        if (pending > 0) return
        // An entry behind to catch Back; pushing also drops entries ahead the app can't replay.
        if ((canBack() && current == 0) || (!canForward() && top > current)) { current++; top = current; history.push(current) }
        // An entry ahead to catch Forward: push it, then step back onto the current one.
        if (canForward() && top == current) { top = current + 1; history.push(top); pending++; history.go(-1) }
    }
}
