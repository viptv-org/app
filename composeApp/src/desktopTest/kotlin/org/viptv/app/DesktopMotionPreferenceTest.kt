package org.viptv.app

import org.viptv.app.features.settings.DesktopMotionPreference
import org.viptv.app.features.settings.rememberSystemReducedMotion
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import org.junit.Rule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopMotionPreferenceTest {
    @get:Rule val uiThread = UiThreadRule()
    @Test fun aPreferenceCreatedDuringCompositionPublishesAnImmediateResult() {
        repeat(10) {
            val preference by lazy { DesktopMotionPreference { false } }
            var observed = true
            val scene = ImageComposeScene(100, 100) {
                observed = rememberSystemReducedMotion { preference.reduced }
            }
            try {
                scene.renderUntil(what = "the preference created during composition") { !observed }
            } finally { scene.close() }
        }
    }

    @Test fun aBlockedSystemQueryReturnsConservativeMotionBeforeItsResult() {
        val release = CountDownLatch(1)
        val queried = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        try {
            val pending = caller.submit<DesktopMotionPreference> {
                DesktopMotionPreference {
                    queried.countDown()
                    release.await()
                    false
                }
            }
            assertTrue(queried.await(5, TimeUnit.SECONDS))
            val preference = pending.get(1, TimeUnit.SECONDS)
            assertTrue(preference.reduced, "Unknown system preference must reduce motion without waiting")
            var observed = false
            val scene = ImageComposeScene(100, 100) {
                observed = rememberSystemReducedMotion { preference.reduced }
            }
            try {
                scene.render(0).close()
                assertTrue(observed, "The first composition sees the conservative pending preference")
                release.countDown()
                scene.renderUntil(what = "the resolved desktop motion preference") { !observed }
                assertFalse(observed, "The system result reaches the existing composition without a restart")
            } finally { scene.close() }
        } finally {
            release.countDown()
            caller.shutdownNow()
        }
    }
}
