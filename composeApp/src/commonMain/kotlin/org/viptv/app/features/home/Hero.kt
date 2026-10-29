package org.viptv.app.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.viptv.engine.wire.Card

@Composable
internal fun Hero(card: Card, compact: Boolean, television: Boolean, open: (Card) -> Unit, play: (Card) -> Unit = open, tablet: Boolean = false, navigate: (org.viptv.app.state.Screen) -> Unit = {}, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false, carousel: HeroCarousel? = null, viewportHeight: Dp? = null, label: String = "Featured") {
    when {
        television -> TvHero(card, open, play, save = save, saved = saved)
        tablet -> TabletHero(card, open, play, navigate, save, saved, carousel)
        compact -> PhoneHero(card, play, save, saved, carousel)
        else -> DesktopHero(card, open, play, browser, save, saved, carousel, viewportHeight, label)
    }
}
