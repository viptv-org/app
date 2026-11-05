package org.viptv.app

import androidx.compose.ui.text.font.FontWeight
import org.viptv.app.features.search.searchMatchHighlight
import kotlin.test.*

class SearchMatchHighlightTest {
    @Test fun noExactSubstringMatchShowsTheTitleUnhighlightedAndUnchanged() {
        val highlighted = searchMatchHighlight("Grotesque", "qqq")
        assertEquals("Grotesque", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty(), "no match means no bold span")
    }

    @Test fun anExactSubstringMatchIsBoldedWithoutAlteringTheText() {
        val highlighted = searchMatchHighlight("Grotesque", "grotesque")
        assertEquals("Grotesque", highlighted.text)
        assertEquals(listOf(0), highlighted.spanStyles.map { it.start })
        assertEquals(listOf(9), highlighted.spanStyles.map { it.end })
        assertTrue(highlighted.spanStyles.all { it.item.fontWeight == FontWeight.Bold })
    }

    @Test fun matchingIgnoresCase() {
        val highlighted = searchMatchHighlight("Grotesque", "GROTESQUE")
        assertEquals("Grotesque", highlighted.text)
        assertEquals(0, highlighted.spanStyles.single().start)
        assertEquals(9, highlighted.spanStyles.single().end)
    }

    @Test fun matchingFoldsDiacritics() {
        val highlighted = searchMatchHighlight("Pokémon", "pokemon")
        assertEquals("Pokémon", highlighted.text)
        assertEquals(0, highlighted.spanStyles.single().start)
        assertEquals("Pokémon".length, highlighted.spanStyles.single().end)
    }

    @Test fun aPartialMatchBoldsOnlyTheMatchedSpan() {
        val highlighted = searchMatchHighlight("Café Society", "cafe")
        assertEquals("Café Society", highlighted.text)
        val bold = highlighted.spanStyles.single()
        assertEquals(0, bold.start)
        assertEquals("Café".length, bold.end)
    }

    @Test fun aMultiCharacterFoldKeepsTheTextUnchangedAndBolded() {
        val highlighted = searchMatchHighlight("Straße", "strasse")
        assertEquals("Straße", highlighted.text)
        assertEquals(0, highlighted.spanStyles.single().start)
        assertEquals("Straße".length, highlighted.spanStyles.single().end)
    }

    @Test fun aTitleThatAlreadyContainsTheQueryIsNeverAltered() {
        val highlighted = searchMatchHighlight("Grotesqqque", "qqq")
        assertEquals("Grotesqqque", highlighted.text)
        assertEquals(listOf("Grotes".length), highlighted.spanStyles.map { it.start })
        assertEquals(listOf("Grotesqqq".length), highlighted.spanStyles.map { it.end })
    }

    @Test fun aBlankQueryLeavesTheTitlePlain() {
        val highlighted = searchMatchHighlight("Grotesque", "")
        assertEquals("Grotesque", highlighted.text)
        assertTrue(highlighted.spanStyles.isEmpty())
    }
}