package org.viptv.app.player

import android.util.Rational
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidPictureInPictureTest {
    @Test fun extremeWideVideoClampsToThePlatformMaximum() {
        assertEquals(Rational(239, 100), clampPipAspectRatio(3000, 100))
    }

    @Test fun extremeTallVideoClampsToThePlatformMinimum() {
        assertEquals(Rational(100, 239), clampPipAspectRatio(100, 3000))
    }

    @Test fun zeroSizedVideoFallsBackToWidescreen() {
        assertEquals(Rational(16, 9), clampPipAspectRatio(0, 0))
    }

    @Test fun unknownSizedVideoFallsBackToWidescreen() {
        assertEquals(Rational(16, 9), clampPipAspectRatio(0, 1080))
        assertEquals(Rational(16, 9), clampPipAspectRatio(1920, -1))
    }

    @Test fun normalVideoKeepsItsAspectRatio() {
        assertEquals(Rational(16, 9), clampPipAspectRatio(1920, 1080))
    }
}