package org.viptv.app

import org.viptv.app.features.settings.rememberDesktopMotion
import org.viptv.app.features.settings.rememberSystemReducedMotion
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopMotionPreferenceTest {
    @get:Rule val uiThread = UiThreadRule()

    @Test fun aPreferenceCreatedDuringCompositionPublishesAnImmediateResult() {
        repeat(10) {
            var observed = true
            val scene = ImageComposeScene(100, 100) {
                observed = rememberSystemReducedMotion { rememberDesktopMotion { false } }
            }
            try {
                scene.renderUntil(what = "the preference created during composition") { !observed }
            } finally { scene.close() }
        }
    }

    @Test fun aBlockedSystemQueryReturnsConservativeMotionBeforeItsResult() {
        val release = CountDownLatch(1)
        val queried = CountDownLatch(1)
        var observed = false
        val scene = ImageComposeScene(100, 100) {
            observed = rememberSystemReducedMotion {
                rememberDesktopMotion {
                    queried.countDown()
                    release.await(2, TimeUnit.SECONDS)
                    false
                }
            }
        }
        try {
            scene.render(0).close()
            assertTrue(queried.await(5, TimeUnit.SECONDS))
            assertTrue(observed, "The first composition must not wait for the system query")
            release.countDown()
            scene.renderUntil(what = "the resolved desktop motion preference") { !observed }
            assertFalse(observed, "The result reaches the existing composition without a restart")
        } finally {
            release.countDown()
            scene.close()
        }
    }
}
