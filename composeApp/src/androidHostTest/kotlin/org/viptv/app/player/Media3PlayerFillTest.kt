package org.viptv.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Media3PlayerFillTest {
    private fun playerView() = PlayerView(RuntimeEnvironment.getApplication())

    @Test fun fitContainsTheWholePicture() {
        val view = playerView()
        view.applyFill(false)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.resizeMode)
    }

    @Test fun fillCoversTheViewportWithACentredCrop() {
        val view = playerView()
        view.applyFill(true)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, view.resizeMode)
    }
}