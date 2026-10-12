package org.viptv.app.features.home

import androidx.compose.runtime.Composable
import org.viptv.engine.wire.Card

@Composable
fun Hero(card: Card, compact: Boolean, television: Boolean, open: (Card) -> Unit, play: (Card) -> Unit = open, tablet: Boolean = false, navigate: (org.viptv.app.state.Screen) -> Unit = {}, browser: Boolean = false) {
    when {
        television -> TvHero(card, open, play)
        tablet -> TabletHero(card, open, play, navigate)
        compact -> PhoneHero(card, play)
        else -> DesktopHero(card, open, play, browser)
    }
}
