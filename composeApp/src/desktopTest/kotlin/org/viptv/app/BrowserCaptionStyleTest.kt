package org.viptv.app

import org.viptv.app.connection.ProfilePreferences
import org.viptv.app.player.browserCaptionDeclarations
import org.viptv.app.player.matchingProviderSubtitle
import org.viptv.engine.wire.SubtitleCandidate
import kotlin.test.*

class BrowserCaptionStyleTest {
    @Test fun defaultPreferencesPreserveAuthoredCueStyles() {
        assertEquals("", browserCaptionDeclarations(ProfilePreferences(), 1f))
        assertEquals("", browserCaptionDeclarations(ProfilePreferences(), Float.NaN))
    }

    @Test fun androidArgbColorsRetainAlphaWhenConvertedToCss() {
        val css = browserCaptionDeclarations(ProfilePreferences(subtitle_text_color = "#80fFeEaa", subtitle_background_color = "#40000000"), 1.25f)
        assertContains(css, "color:#FFEEAA80;")
        assertContains(css, "background-color:#00000040;")
        assertContains(css, "font-size:6.25vh;")
    }

    @Test fun malformedStyleTextCannotBecomeCssAndSizeStaysBounded() {
        val preferences = ProfilePreferences(subtitle_text_color = "red;}body{display:none", subtitle_background_color = "url(https://invalid)")
        assertEquals("font-size:10.0vh;", browserCaptionDeclarations(preferences, 99f))
        assertEquals("font-size:2.5vh;", browserCaptionDeclarations(preferences, -1f))
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
