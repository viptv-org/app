package org.viptv.app

import kotlin.test.*
import org.viptv.app.state.withCardArt
import org.viptv.engine.wire.*

/**
 * Sparse metas (Cinemeta's live endpoint returns no art fields for obscure
 * titles) must not blank the details page when the clicked card had art.
 */
class SparseDetailArtTest {
    private val card = Card(MediaIdentity("tt33673360", "series", emptyList()), provider_id = "cinemeta",
        title = "Gabby & Friends", poster = "https://m.media-amazon.com/poster.jpg",
        landscape = "https://images.metahub.space/back.jpg", logo = "https://images.metahub.space/logo.png", badges = emptyList())

    @Test fun sparseDetailInheritsTheClickedCardsArt() {
        val sparse = DetailSnapshot(item = card.copy(poster = null, landscape = null, logo = null), episodes = emptyList())
        val merged = sparse.withCardArt(card)
        assertEquals("https://images.metahub.space/back.jpg", merged.item.landscape)
        assertEquals("https://m.media-amazon.com/poster.jpg", merged.item.poster)
        assertEquals("https://images.metahub.space/logo.png", merged.item.logo)
    }

    @Test fun detailArtWinsOverTheCardWhenPresent() {
        val full = DetailSnapshot(item = card.copy(poster = "https://images.metahub.space/better.jpg"), episodes = emptyList())
        assertEquals("https://images.metahub.space/better.jpg", full.withCardArt(card).item.poster)
        assertSame(full, full.withCardArt(card)) // unchanged instance when nothing merges
    }
}
