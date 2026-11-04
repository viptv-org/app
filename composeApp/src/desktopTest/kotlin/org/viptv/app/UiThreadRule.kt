package org.viptv.app

import java.awt.EventQueue
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// Compose posts layout callbacks to the Swing event thread ~16 ms later; a worker thread driving the same scene corrupts its RectList.
class UiThreadRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            // A Recomposer failure is reported by the next test's uncaught-exception handler; capture it so the test that raced composition fails.
            val compositionLeaks = CopyOnWriteArrayList<Throwable>()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                if (throwable is IllegalStateException && throwable.message?.contains("Unsupported concurrent change during composition") == true) {
                    compositionLeaks += throwable
                }
                previous?.uncaughtException(thread, throwable)
            }
            try {
                evaluateOnEventThread(base)
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(previous)
            }
            compositionLeaks.firstOrNull()?.let { throw AssertionError("A composition state change leaked to another coroutine during this test", it) }
        }

        private fun evaluateOnEventThread(base: Statement) {
            if (EventQueue.isDispatchThread()) {
                base.evaluate()
                return
            }
            var failure: Throwable? = null
            EventQueue.invokeAndWait { try { base.evaluate() } catch (t: Throwable) { failure = t } }
            failure?.let { throw it }
        }
    }
}
