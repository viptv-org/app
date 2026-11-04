package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.features.settings.profilePreferences
import org.viptv.app.features.settings.tvSettings
import org.viptv.app.player.CAPTION_NO_BOX
import org.viptv.app.player.browserCaptionDeclarations
import org.viptv.app.player.captionBoxArgb
import org.viptv.app.player.desktopSubtitleConfig
import org.viptv.app.player.mpv.mpvSubtitleBoxOptions
import kotlin.test.*

class CaptionBackgroundNoneTest {
    private val noBox = ProfilePreferences(subtitle_background_color = CAPTION_NO_BOX)
    private val boxed = ProfilePreferences(subtitle_background_color = "#B3000000")

    @Test fun browserCancelsItsOwnDefaultCueBoxForNone() {
        assertEquals("background-color:transparent;", browserCaptionDeclarations(noBox, 1f))
        assertEquals("background-color:#000000B3;", browserCaptionDeclarations(boxed, 1f))
    }

    @Test fun desktopRendererDrawsNoBoxForNone() {
        assertNull(desktopSubtitleConfig(noBox).style?.backgroundColor)
        assertEquals(0xB3000000.toInt(), desktopSubtitleConfig(boxed).style?.backgroundColor)
    }

    @Test fun mpvClearsItsBoxForNoneAndDrawsOneForBox() {
        assertEquals(listOf("sub-back-color" to "#00000000", "sub-border-style" to "outline-and-shadow"),
            mpvSubtitleBoxOptions(captionBoxArgb(CAPTION_NO_BOX)))
        assertEquals(listOf("sub-back-color" to "#B3000000", "sub-border-style" to "background-box"),
            mpvSubtitleBoxOptions(captionBoxArgb("#B3000000")))
    }

    @Test fun everyRendererAgreesThatNoneHasNoBox() {
        listOf(CAPTION_NO_BOX, "#00000000", "#00FFFFFF", "", "not-a-colour").forEach { value ->
            val preferences = ProfilePreferences(subtitle_background_color = value)
            assertNull(captionBoxArgb(value), value)
            assertNull(desktopSubtitleConfig(preferences).style?.backgroundColor, value)
            assertEquals("outline-and-shadow", mpvSubtitleBoxOptions(captionBoxArgb(value)).last().second, value)
            assertEquals("background-color:transparent;", browserCaptionDeclarations(preferences, 1f), value)
        }
        listOf("#B3000000", "#FF000000", "#000000").forEach { value ->
            val preferences = ProfilePreferences(subtitle_background_color = value)
            assertNotNull(captionBoxArgb(value), value)
            assertNotNull(desktopSubtitleConfig(preferences).style?.backgroundColor, value)
            assertEquals("background-box", mpvSubtitleBoxOptions(captionBoxArgb(value)).last().second, value)
            assertContains(browserCaptionDeclarations(preferences, 1f), "background-color:#")
        }
    }

    @Test fun aSavedNoneReachesEveryRendererAfterARestart() {
        val saved = boxed.tvSettings().copy(subtitleBackground = "None").profilePreferences(boxed)
        assertEquals(CAPTION_NO_BOX, saved.subtitle_background_color)
        assertNull(captionBoxArgb(saved.subtitle_background_color))
        assertNull(desktopSubtitleConfig(saved).style?.backgroundColor)
        assertEquals("outline-and-shadow", mpvSubtitleBoxOptions(captionBoxArgb(saved.subtitle_background_color)).last().second)
        assertEquals("background-color:transparent;", browserCaptionDeclarations(saved, 1f))
    }
}
