package org.viptv.app

import org.viptv.app.features.search.tvVoiceInputAvailable
import org.viptv.app.features.search.tvVoiceRecognitionText
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvVoiceSearchTest {
    @Test fun microphoneIsAvailableOnlyOnTelevisionsWithARecognizer() {
        assertTrue(tvVoiceInputAvailable(television = true, recognizerAvailable = true))
        assertFalse(tvVoiceInputAvailable(television = false, recognizerAvailable = true))
        assertFalse(tvVoiceInputAvailable(television = true, recognizerAvailable = false))
    }

    @Test fun onlySuccessfulNonBlankRecognitionReplacesTheQuery() {
        assertEquals("Star Trek", tvVoiceRecognitionText(true, listOf("Star Trek")))
        assertNull(tvVoiceRecognitionText(false, listOf("ignored")))
        assertNull(tvVoiceRecognitionText(true, null))
        assertNull(tvVoiceRecognitionText(true, listOf(" ")))
    }
}
