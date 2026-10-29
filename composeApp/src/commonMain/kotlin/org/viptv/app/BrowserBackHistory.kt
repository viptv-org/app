package org.viptv.app

interface SessionHistory {
    fun push(index: Int)
    fun replace(index: Int)
    fun go(delta: Int)
}

class BrowserBackHistory(
    private val history: SessionHistory,
    private val canBack: () -> Boolean,
    private val canForward: () -> Boolean,
    private val back: () -> Unit,
    private val forward: () -> Unit,
) {
    private var current = 0
    private var top = 0
    private var pending = 0

    fun start() { history.replace(0); settle() }

    fun changed() = settle()

    fun moved(index: Int?) {
        if (pending > 0) { pending--; if (index != null) current = index; settle(); return }
        if (index == null) { history.go(-1); return }
        val steps = index - current
        current = index
        when {
            steps < 0 && !canBack() -> { history.go(-(index + 1)); return }
            steps < 0 -> repeat(-steps) { back() }
            steps > 0 -> repeat(steps) { forward() }
        }
        settle()
    }

    private fun settle() {
        if (pending > 0) return
        // An entry behind to catch Back; pushing also drops entries ahead the app cannot replay.
        if ((canBack() && current == 0) || (!canForward() && top > current)) { current++; top = current; history.push(current) }
        // An entry ahead to catch Forward: push it, then step back onto the current one.
        if (canForward() && top == current) { top = current + 1; history.push(top); pending++; history.go(-1) }
    }
}
