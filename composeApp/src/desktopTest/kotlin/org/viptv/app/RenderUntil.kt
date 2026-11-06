package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalComposeUiApi::class)
fun ImageComposeScene.renderUntil(timeout: Duration = 5.seconds, from: Long = 0L, what: String = "the expected state",
    condition: () -> Boolean): Long {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var frame = from
    while (true) {
        render(frame++ * 16_666_667L).close()
        if (condition()) return frame
        if (deadline.hasPassedNow()) fail("the scene did not reach $what within $timeout")
        Thread.yield()
    }
}