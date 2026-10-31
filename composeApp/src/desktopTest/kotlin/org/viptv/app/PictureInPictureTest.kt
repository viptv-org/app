package org.viptv.app

import org.viptv.app.player.PictureInPictureBlocker
import org.viptv.app.player.pictureInPictureBlocker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PictureInPictureTest {
    @Test fun aPlayingSupportedStreamOnAPhoneLeavesForAPictureInPictureWindow() {
        assertNull(pictureInPictureBlocker(television = false, playing = true, streamSupports = true, deviceSupports = true))
    }

    @Test fun aPausedPlayerKeepsTheAppOnScreen() {
        assertEquals(PictureInPictureBlocker.NotPlaying, pictureInPictureBlocker(television = false, playing = false, streamSupports = true, deviceSupports = true))
    }

    @Test fun televisionKeepsItsOwnPlayer() {
        assertEquals(PictureInPictureBlocker.Television, pictureInPictureBlocker(television = true, playing = true, streamSupports = true, deviceSupports = true))
    }

    @Test fun aStreamThatCannotDrawIntoAPictureInPictureWindowKeepsTheAppOnScreen() {
        assertEquals(PictureInPictureBlocker.Stream, pictureInPictureBlocker(television = false, playing = true, streamSupports = false, deviceSupports = true))
    }

    @Test fun aDeviceWithoutPictureInPictureKeepsTheAppOnScreen() {
        assertEquals(PictureInPictureBlocker.Device, pictureInPictureBlocker(television = false, playing = true, streamSupports = true, deviceSupports = false))
    }
}
