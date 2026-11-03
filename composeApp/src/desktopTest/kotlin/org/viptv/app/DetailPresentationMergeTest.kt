package org.viptv.app

import kotlin.test.*
import org.viptv.app.state.withCardPresentation
import org.viptv.engine.wire.*

class DetailPresentationMergeTest {
    private val identity = MediaIdentity("tt33673360", "series", emptyList())
    private fun card(description: String? = "Card description", poster: String? = "https://card/poster.jpg",
        landscape: String? = "https://card/back.jpg", logo: String? = "https://card/logo.png") =
        Card(identity, provider_id = "cinemeta", title = "Gabby & Friends", description = description,
            poster = poster, landscape = landscape, logo = logo, badges = emptyList())

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
}
