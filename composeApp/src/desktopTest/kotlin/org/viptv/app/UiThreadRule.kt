package org.viptv.app

import java.awt.EventQueue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Runs each test (with its before/after hooks) on the Swing event thread.
 *
 * Compose desktop posts `RectManager` layout callbacks to `MainUIDispatcher`,
 * the Swing event thread, about 16 ms after a layout change. A test that drives
 * an `ImageComposeScene` from the JUnit worker thread lets such a callback run
 * on the event thread while the worker renders, lays out or closes the same
 * scene. The two threads then corrupt the scene's `RectList` ("LayoutNode N
 * not found in RectList"), which fails the test, or escapes through the
 * Recomposer's job and fails a later `runTest` with
 * `UncaughtExceptionsBeforeTest` (app#16, app#27).
 *
 * While the test holds the event thread, a posted callback waits: `render`
 * dispatches the same callbacks on the test thread, and `close` cancels the
 * pending one. Every desktop test class that builds an `ImageComposeScene`
 * declares `@get:Rule val uiThread = UiThreadRule()`; `UiThreadConfinementTest`
 * enforces that.
 */
class UiThreadRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            if (EventQueue.isDispatchThread()) return base.evaluate()
            var failure: Throwable? = null
            EventQueue.invokeAndWait { try { base.evaluate() } catch (t: Throwable) { failure = t } }
            failure?.let { throw it }
        }
    }
}
