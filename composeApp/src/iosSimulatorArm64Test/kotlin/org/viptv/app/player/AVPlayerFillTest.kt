package org.viptv.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill

class AVPlayerFillTest {
    @Test fun fitShowsTheWholeFrame() = assertEquals(AVLayerVideoGravityResizeAspect, fillVideoGravity(false))

    @Test fun fillCoversTheViewportWithACentredCrop() = assertEquals(AVLayerVideoGravityResizeAspectFill, fillVideoGravity(true))

    @Test fun theTwoModesDiffer() = assertNotEquals(fillVideoGravity(false), fillVideoGravity(true))
}
