package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.browserCaptionDeclarations
import org.viptv.app.player.matchingProviderSubtitle
import org.viptv.app.player.providerCueLine
import org.viptv.engine.wire.SubtitleCandidate
import kotlin.test.*

class BrowserCaptionStyleTest {
    @Test fun defaultPreferencesKeepAuthoredCueStylesAndCancelTheBrowserBox() {
        assertEquals("background-color:transparent;", browserCaptionDeclarations(ProfilePreferences(), 1f))
        assertEquals("background-color:transparent;", browserCaptionDeclarations(ProfilePreferences(), Float.NaN))
    }

    @Test fun androidArgbColorsRetainAlphaWhenConvertedToCss() {
        val css = browserCaptionDeclarations(ProfilePreferences(subtitle_text_color = "#80fFeEaa", subtitle_background_color = "#40000000"), 1.25f)
        assertEquals("font-size:6.25vh;color:#FFEEAA80;background-color:#00000040;", css)
    }

    @Test fun malformedStyleTextCannotBecomeCssAndSizeStaysBounded() {
        val preferences = ProfilePreferences(subtitle_text_color = "red;}body{display:none", subtitle_background_color = "url(https://invalid)")
        assertEquals("font-size:10.0vh;background-color:transparent;", browserCaptionDeclarations(preferences, 99f))
        assertEquals("font-size:2.5vh;background-color:transparent;", browserCaptionDeclarations(preferences, -1f))
    }
    @Test fun providerCuesSitOnSnappedLinesLiftedTheSameDistanceAtEverySize() {
        assertEquals(-5, providerCueLine(1f))
        assertEquals(-3, providerCueLine(2f))
        assertEquals(-9, providerCueLine(.5f))
        assertEquals(-4, providerCueLine(1.5f))
        assertEquals(-5, providerCueLine(Float.NaN))
        assertEquals(-3, providerCueLine(99f))
    }
    @Test fun retainedChoiceMatchesNewLeaseIdsOnlyWithinTheSameUnambiguousProviderTrack() {
        val old = SubtitleCandidate("old", "addon:1", "en", "English")
        val renewed = old.copy(id = "new")
        assertEquals(renewed, matchingProviderSubtitle(old, listOf(old.copy(id = "foreign", provider_id = "addon:2"), renewed)))
        assertNull(matchingProviderSubtitle(old, listOf(renewed, renewed.copy(id = "also-new"))))
        assertEquals(old, matchingProviderSubtitle(old, listOf(renewed, old)))
        assertNull(matchingProviderSubtitle(old, listOf(renewed.copy(language = "fr"))))
    }

}
