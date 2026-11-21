package org.viptv.app

import org.viptv.app.player.languageCode
import org.viptv.app.player.languageName
import org.viptv.app.player.languageTag
import org.viptv.app.player.sameLanguage
import kotlin.test.*

class LanguageTagsTest {
    @Test fun twoLetterBibliographicTerminologyCodesAndNamesMeetOnOneCode() {
        mapOf("en" to listOf("en", "eng", "EN-gb", "en_US", "English"), "fr" to listOf("fr", "fre", "fra", "French", "français"),
            "de" to listOf("de", "ger", "deu", "German"), "zh" to listOf("zh", "chi", "zho", "zh-Hant-TW"),
            "ja" to listOf("ja", "jpn", "Japanese"), "sk" to listOf("sk", "slo", "slk"), "he" to listOf("he", "heb", "iw"))
            .forEach { (code, values) -> values.forEach { assertEquals(code, languageCode(it), it) } }
    }

    @Test fun unknownAndUndeterminedValuesNameNoLanguage() {
        assertEquals("tlh", languageCode("tlh"))
        listOf(null, "", " ", "und", "mul", "zxx", "mis", "Original language", "Commentary", "e1").forEach { assertNull(languageCode(it), "$it") }
    }

    @Test fun tagsKeepScriptAndRegionAfterTheCanonicalCode() {
        assertEquals("pt-br", languageTag("por_BR")); assertEquals("pt-br", languageTag("pt-BR"))
        assertEquals("en", languageTag("English")); assertEquals("en", languageTag("eng"))
        assertEquals("zh-hant", languageTag("chi-Hant"))
        assertNull(languageTag("und")); assertNull(languageTag("Default"))
    }

    @Test fun comparisonsAndNamesUseTheSameCode() {
        assertTrue(sameLanguage("eng", "en-US")); assertTrue(sameLanguage("English", "en"))
        assertFalse(sameLanguage("und", "und")); assertFalse(sameLanguage(null, null)); assertFalse(sameLanguage("jpn", "eng"))
        assertEquals("English", languageName("eng")); assertEquals("Japanese", languageName("jpn")); assertEquals("French", languageName("fre"))
        assertEquals("tlh", languageName("tlh")); assertNull(languageName("und"))
    }
}
