package org.viptv.app

import java.awt.EventQueue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

// Compose posts layout callbacks to the Swing event thread ~16 ms later; a worker thread driving the same scene corrupts its RectList.
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
