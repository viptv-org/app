package org.viptv.app.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderPlainTextTest {
    @Test
    fun providerDescriptionWithBreaksBecomesProse() {
        val raw = "Across the Fiore kingdom, wizards join guilds and make their pay by filling " +
            "magical needs\u2014but one guild has a reputation as the roughest, rowdiest, most dangerous " +
            "of all: Fairy Tail!<br>\n" +
            "When four young Fairy Tail members unite, ... <br>\n" +
            "<br>\n" +
            "(Source: Funimation)"
        val result = providerPlainText(raw)
        assertFalse(result.contains("<br"), "no br tag remains: $result")
        assertFalse(Regex("&[A-Za-z#0-9]+;").containsMatchIn(result), "no entity remains: $result")
        assertTrue(result.contains("Fiore kingdom"), "words are kept: $result")
        assertTrue(result.contains("(Source: Funimation)"), "attribution is kept: $result")
        assertEquals(
            "Across the Fiore kingdom, wizards join guilds and make their pay by filling " +
                "magical needs\u2014but one guild has a reputation as the roughest, rowdiest, most dangerous " +
                "of all: Fairy Tail!\n" +
                "When four young Fairy Tail members unite, ...\n" +
                "\n" +
                "(Source: Funimation)",
            result)
    }

    @Test
    fun breakTagVariantsBecomeLineBreaks() {
        assertEquals("one\ntwo\nthree\nfour", providerPlainText("one<br/>two<br />three<BR>four"))
    }

    @Test
    fun otherTagsAreRemovedButKeepTheirText() {
        assertEquals("wrapped in italics", providerPlainText("wrapped <i>in</i> italics"))
        assertEquals("First paragraph\n\nSecond.", providerPlainText("<p>First <b>paragraph</b></p><p>Second.</p>"))
        assertEquals("Intro.\n\nNew paragraph.", providerPlainText("Intro.<p>New paragraph."))
        assertEquals("First\n\nSecond", providerPlainText("First</p>\n\nSecond"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Fish & Chips", providerPlainText("Fish &amp; Chips"))
        assertEquals("it's", providerPlainText("it&#39;s"))
        assertEquals("one two", providerPlainText("one&nbsp;two"))
        assertEquals("\"quoted\"", providerPlainText("&quot;quoted&quot;"))
    }

    @Test
    fun entitiesAreNotDoubleDecoded() {
        assertEquals("&amp;", providerPlainText("&amp;amp;"))
        assertEquals("&#39;", providerPlainText("&amp;#39;"))
    }

    @Test
    fun plainTextPassesThrough() {
        val plain = "Tom & Jerry, 3 > 2 \u2014 plain."
        assertEquals(plain, providerPlainText(plain))
    }

    @Test
    fun surfacesDecodeOnceAndDropWhatDecodesToNothing() {
        assertEquals(null, (null as String?).providerText())
        assertEquals(null, "<br>".providerText())
        assertEquals("Fish & Chips", "Fish &amp; Chips".providerText())
        assertEquals("&amp;", "&amp;amp;".providerText())
    }
}
