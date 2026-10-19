package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.app.components.DesktopBreakpoints
import org.viptv.app.components.MediaMetadata
import org.viptv.engine.wire.Card

@Composable
internal fun DesktopHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false, carousel: HeroCarousel? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = if (browser) 57.dp else 28.dp, bottom = if (browser) 88.dp else 80.dp)) {
        if (maxWidth < DesktopBreakpoints.heroStackMax) {
            // D3HomeNarrow: artwork full width, copy under it, 22px apart.
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                HeroArtwork(card, Modifier.fillMaxWidth(), carousel)
                FeaturedSummary(card, open, play, save, saved, Modifier.fillMaxWidth(), carousel)
            }
        } else {
            // D3HomeCompact/Win/Mac/Ultra: copy left, artwork flush right. The old
            // 0.45/0.55 weights were proportional, so the 2560 board rendered a
            // 1370dp hero still instead of the specified 774dp.
            val gap = 48.dp
            val artWidth = (maxWidth * DesktopBreakpoints.heroArtFraction)
                .coerceAtMost(DesktopBreakpoints.heroArtMax)
            val copyWidth = (maxWidth - gap - artWidth).coerceAtMost(
                if (maxWidth >= DesktopBreakpoints.heroWideMin) DesktopBreakpoints.heroSummaryWideMax
                else DesktopBreakpoints.heroSummaryMax)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                FeaturedSummary(card, open, play, save, saved, Modifier.width(copyWidth), carousel)
                HeroArtwork(card, Modifier.width(artWidth), carousel)
            }
        }
    }
}

@Composable
private fun HeroArtwork(card: Card, modifier: Modifier, carousel: HeroCarousel?) {
    val heroArt = card.landscape ?: card.poster
    if (heroArt == null) HeroArtworkFallback(modifier.aspectRatio(16f / 9)) else BufferedArtwork(heroArt,
        modifier.aspectRatio(16f / 9).clip(RoundedCornerShape(12.dp)).background(Palette.surface),
        onReady = { carousel?.artworkReady(it) })
}

@Composable
private fun FeaturedSummary(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean, modifier: Modifier, carousel: HeroCarousel?) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("FEATURED", fontSize = 13.sp, letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold, color = Palette.secondary)
        if (card.logo != null) {
            AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title, Modifier.width(220.dp).height(120.dp),
                alignment = Alignment.BottomStart, contentScale = ContentScale.Fit)
        } else {
            Text(card.title, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
        }
        MediaMetadata(card)
        card.description?.let {
            Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body, maxLines = 3)
        }
        DesktopHeroActions({ play(card) }, { open(card) }, { save(card) }, saved)
        carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
    }
}
