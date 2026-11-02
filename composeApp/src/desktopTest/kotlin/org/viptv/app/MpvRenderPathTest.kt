package org.viptv.app

import org.viptv.app.player.mpv.*
import kotlin.test.*

class MpvRenderPathTest {
    @Test fun theGlPathIsOptIn() {
        assertFalse(mpvGlRequested(property = null, environment = null))
        assertFalse(mpvGlRequested(property = "false", environment = "0"))
        assertTrue(mpvGlRequested(property = "true", environment = null))
        assertTrue(mpvGlRequested(property = "TRUE", environment = null))
        assertTrue(mpvGlRequested(property = null, environment = "1"))
    }

    @Test fun softwareUnlessRequested() {
        for (state in MpvGlState.entries) assertEquals(MpvRenderPath.Software, mpvRenderPath(requested = false, state, pendingFrames = 0))
    }

    @Test fun requestedGlNeedsSkikosGlContext() {
        assertEquals(MpvRenderPath.Gl, mpvRenderPath(requested = true, MpvGlState.Ready, pendingFrames = 0))
        assertEquals(MpvRenderPath.Software, mpvRenderPath(requested = true, MpvGlState.Unavailable, pendingFrames = 0))
    }

    @Test fun waitsForSkikosFirstGlFrameThenGivesUp() {
        assertNull(mpvRenderPath(requested = true, MpvGlState.Pending, pendingFrames = 0))
        assertNull(mpvRenderPath(requested = true, MpvGlState.Pending, pendingFrames = mpvGlPendingFrameLimit - 1))
        assertEquals(MpvRenderPath.Software, mpvRenderPath(requested = true, MpvGlState.Pending, pendingFrames = mpvGlPendingFrameLimit))
    }

    @Test fun pathLabelsMatchTheDiagnostic() {
        assertEquals("mpv-software", MpvRenderPath.Software.label)
        assertEquals("mpv-gl", MpvRenderPath.Gl.label)
    }
}
