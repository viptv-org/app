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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.app.components.DesktopBreakpoints
import org.viptv.app.components.MediaMetadata
import org.viptv.engine.wire.Card

internal const val HERO_ARTWORK_TAG = "hero-artwork"
internal const val HERO_SUMMARY_TAG = "hero-summary"

@Composable
internal fun DesktopHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, browser: Boolean = false, save: (Card) -> Unit = {}, saved: Boolean = false, carousel: HeroCarousel? = null, viewportHeight: Dp? = null, label: String = "Featured") {
    val artHeight = viewportHeight?.takeIf { it < DesktopBreakpoints.heroShortViewport }
        ?.let { (it * DesktopBreakpoints.heroShortArtFraction).coerceAtMost(DesktopBreakpoints.heroShortArtMax) }
    val short = artHeight != null
    val rowMin = if (short) DesktopBreakpoints.heroShortRowMin else DesktopBreakpoints.heroStackMax
    BoxWithConstraints(Modifier.fillMaxWidth().padding(
        top = if (short) 12.dp else if (browser) 57.dp else 28.dp,
        bottom = if (short) 16.dp else if (browser) 88.dp else 80.dp)) {
        if (maxWidth < rowMin) {
            Column(verticalArrangement = Arrangement.spacedBy(if (short) 14.dp else 22.dp)) {
                HeroArtwork(card, Modifier.fillMaxWidth(), artHeight, carousel)
                FeaturedSummary(card, open, play, save, saved, Modifier.fillMaxWidth(), carousel, short, label)
            }
        } else {
            val gap = if (short) 32.dp else 48.dp
            val artWidth = artHeight?.times(16f / 9f)
                ?: (maxWidth * DesktopBreakpoints.heroArtFraction).coerceAtMost(DesktopBreakpoints.heroArtMax)
            val copyWidth = (maxWidth - gap - artWidth).coerceAtMost(
                if (maxWidth >= DesktopBreakpoints.heroWideMin) DesktopBreakpoints.heroSummaryWideMax
                else DesktopBreakpoints.heroSummaryMax)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                FeaturedSummary(card, open, play, save, saved, Modifier.width(copyWidth), carousel, short, label)
                HeroArtwork(card, Modifier.width(artWidth), artHeight, carousel)
            }
        }
    }
}

@Composable
private fun HeroArtwork(card: Card, modifier: Modifier, height: Dp?, carousel: HeroCarousel?) {
    // A blank URL is no artwork at all: decoding an empty string paints a flat fill.
    val heroArt = (card.landscape ?: card.poster)?.takeIf { it.isNotBlank() }
    val framed = if (height != null) modifier.height(height) else modifier.aspectRatio(16f / 9)
    if (heroArt == null) HeroArtworkFallback(framed) else BufferedArtwork(heroArt,
        framed.clip(RoundedCornerShape(12.dp)).background(Palette.surface).testTag(HERO_ARTWORK_TAG),
        onReady = { carousel?.artworkReady(it) })
}

@Composable
private fun FeaturedSummary(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean, modifier: Modifier, carousel: HeroCarousel?, short: Boolean, label: String) {
    Column(modifier.testTag(HERO_SUMMARY_TAG), verticalArrangement = Arrangement.spacedBy(if (short) 12.dp else 14.dp)) {
        Text(if (carousel != null && carousel.count > 1) "$label · ${carousel.index + 1} of ${carousel.count}" else label,
            fontSize = 13.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
        if (card.logo != null) {
            AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title,
                Modifier.width(if (short) 140.dp else 220.dp).height(if (short) 64.dp else 120.dp),
                alignment = Alignment.BottomStart, contentScale = ContentScale.Fit)
        } else {
            Text(card.title, fontSize = if (short) 26.sp else 36.sp, lineHeight = if (short) 30.sp else 40.sp, fontWeight = FontWeight.Bold)
        }
        MediaMetadata(card)
        card.description?.let {
            Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.body, maxLines = if (short) 2 else 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        DesktopHeroActions({ play(card) }, { open(card) }, { save(card) }, saved)
        carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
    }
}
