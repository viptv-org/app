package org.viptv.app.player

import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTagsTest {
    @Test fun canonicalizesTwoLetterCodes() {
        assertEquals("en", canonicalLanguageTag("EN"))
        assertEquals("pt-br", canonicalLanguageTag("PT_BR"))
    }

    @Test fun canonicalizesBibliographicAliases() {
        assertEquals("fra", canonicalLanguageTag("fre"))
        assertEquals("deu", canonicalLanguageTag("GER"))
        assertEquals("zho", canonicalLanguageTag("chi"))
    }

    @Test fun leavesUnknownPartsUntouched() {
        assertEquals("xx", canonicalLanguageTag("xx"))
        assertEquals("", canonicalLanguageTag("  "))
    }
}
