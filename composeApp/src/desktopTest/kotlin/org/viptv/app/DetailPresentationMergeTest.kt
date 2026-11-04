package org.viptv.app

import kotlin.test.*
import org.viptv.app.state.withCardPresentation
import org.viptv.engine.wire.*

class DetailPresentationMergeTest {
    private val identity = MediaIdentity("tt33673360", "series", emptyList())
    private fun card(description: String? = "Card description", poster: String? = "https://card/poster.jpg",
        landscape: String? = "https://card/back.jpg", logo: String? = "https://card/logo.png",
        metadata: CardMetadata? = null, badges: List<String> = emptyList()) =
        Card(identity, provider_id = "cinemeta", title = "Gabby & Friends", description = description,
            poster = poster, landscape = landscape, logo = logo, metadata = metadata, badges = badges)

    @Test fun sparseDetailKeepsTheCardsDescriptionAndArt() {
        val sparse = DetailSnapshot(item = card().copy(description = null, poster = null, landscape = null, logo = null), episodes = emptyList())
        val merged = sparse.withCardPresentation(card())
        assertEquals("Card description", merged.item.description)
        assertEquals("https://card/back.jpg", merged.item.landscape)
        assertEquals("https://card/poster.jpg", merged.item.poster)
        assertEquals("https://card/logo.png", merged.item.logo)
    }

    @Test fun aLaterSparserDescriptionNeverRemovesTheCard() {
        val loaded = DetailSnapshot(item = card(description = null), episodes = emptyList())
        assertEquals("Card description", loaded.withCardPresentation(card()).item.description)

        val shorter = DetailSnapshot(item = card(description = "Short"), episodes = emptyList())
        assertEquals("Card description", shorter.withCardPresentation(card()).item.description)
    }

    @Test fun aLongerLoadedDescriptionWins() {
        val loaded = DetailSnapshot(item = card(description = "A much longer description than the card"), episodes = emptyList())
        assertEquals("A much longer description than the card", loaded.withCardPresentation(card()).item.description)
    }

    @Test fun cardArtStaysStableOverDifferentDetailArt() {
        val loaded = DetailSnapshot(item = card(poster = "https://detail/different.jpg", landscape = "https://detail/bg.jpg", logo = "https://detail/logo.png"), episodes = emptyList())
        val merged = loaded.withCardPresentation(card())
        assertEquals("https://card/poster.jpg", merged.item.poster)
        assertEquals("https://card/back.jpg", merged.item.landscape)
        assertEquals("https://card/logo.png", merged.item.logo)
    }

    @Test fun placeholderCardArtFallsBackToTheDetail() {
        val cardWithoutArt = card(poster = null, landscape = null, logo = null)
        val loaded = DetailSnapshot(item = card(), episodes = emptyList())
        val merged = loaded.withCardPresentation(cardWithoutArt)
        assertEquals("https://card/poster.jpg", merged.item.poster)
        assertEquals("https://card/back.jpg", merged.item.landscape)
        assertEquals("https://card/logo.png", merged.item.logo)
    }

    @Test fun equalLengthDescriptionKeepsTheFirstPaintedCard() {
        val loaded = DetailSnapshot(item = card(description = "Same length text"), episodes = emptyList())
        assertEquals("Card description", loaded.withCardPresentation(card(description = "Card description")).item.description)
    }

    @Test fun untouchedWhenEverythingAlreadyMatches() {
        val loaded = DetailSnapshot(item = card(), episodes = emptyList())
        assertSame(loaded, loaded.withCardPresentation(card()))
    }

    @Test fun theMergeKeepsTheDetailsMetadata() {
        val loaded = DetailSnapshot(item = card().copy(metadata = CardMetadata(cast = listOf("A", "B", "C"))), episodes = emptyList())
        assertEquals(listOf("A", "B", "C"), loaded.withCardPresentation(card()).item.metadata?.cast)
    }

    @Test fun aSparserDetailKeepsTheCardsMetadataLine() {
        val tapped = card().copy(metadata = CardMetadata(year = 2008, runtime_minutes = 47, genres = listOf("Crime", "Drama"), rating = 9.5))
        val loaded = DetailSnapshot(item = card(metadata = CardMetadata(year = 2008)), episodes = emptyList())
        val merged = loaded.withCardPresentation(tapped).item
        assertEquals(2008, merged.metadata?.year)
        assertEquals(47, merged.metadata?.runtime_minutes)
        assertEquals(listOf("Crime", "Drama"), merged.metadata?.genres)
        assertEquals(9.5, merged.metadata?.rating)
    }

    @Test fun aRichCastArrivingFillsTheBlankCastAndNeverDowngrades() {
        val tapped = card(metadata = CardMetadata(cast = emptyList())).copy(metadata = CardMetadata(year = 2008))
        val loaded = DetailSnapshot(item = card(metadata = CardMetadata(year = null, cast = listOf("A", "B", "C"))), episodes = emptyList())
        val merged = loaded.withCardPresentation(tapped).item
        assertEquals(listOf("A", "B", "C"), merged.metadata?.cast)
        assertEquals(2008, merged.metadata?.year)
    }

    @Test fun badgesOnlyGrow() {
        val tapped = card(badges = listOf("4K"))
        val loaded = DetailSnapshot(item = card(badges = listOf("4K", "HDR", "CC")), episodes = emptyList())
        assertEquals(listOf("4K", "HDR", "CC"), loaded.withCardPresentation(tapped).item.badges)
        val sparser = DetailSnapshot(item = card(badges = emptyList()), episodes = emptyList())
        assertEquals(listOf("4K"), sparser.withCardPresentation(tapped).item.badges)
    }

    @Test fun theTitleKeepsTheFirstShownValue() {
        val loaded = DetailSnapshot(item = card().copy(title = "A different title"), episodes = emptyList())
        assertEquals("Gabby & Friends", loaded.withCardPresentation(card()).item.title)
    }
}
