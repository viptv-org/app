package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.components.DesktopBreakpoints
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.components.rememberTvLoadingShimmer
import org.viptv.app.theme.Palette

@Composable
internal fun HomeSkeleton(compact: Boolean, tablet: Boolean, browser: Boolean, gutter: Dp, viewportHeight: Dp? = null) {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading Home" },
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = if (tablet || browser) 0.dp else 12.dp)) {
            when {
                !compact && !tablet -> DesktopHeroSkeleton(browser, gutter)
                tablet -> TabletHeroSkeleton(gutter, viewportHeight)
                else -> PhoneHeroSkeleton(gutter)
            }
        }
        SkeletonShelf(portrait = false, compact = compact, tablet = tablet, gutter = gutter)
        SkeletonShelf(portrait = true, compact = compact, tablet = tablet, gutter = gutter)
        SkeletonShelf(portrait = true, compact = compact, tablet = tablet, gutter = gutter)
    }
}

@Composable
private fun Block(modifier: Modifier, radius: Dp = 8.dp, raised: Boolean = false) {
    val shimmer = if (LocalReducedMotion.current) Modifier else rememberTvLoadingShimmer(1f)
    Box(modifier.clip(RoundedCornerShape(radius))
        .background(if (raised) Palette.raised else Palette.surface)
        .then(shimmer))
}

@Composable
private fun PhoneHeroSkeleton(gutter: Dp) {
    Box(Modifier.fillMaxWidth().padding(horizontal = gutter).height(470.dp)) {
        Block(Modifier.fillMaxSize(), radius = 16.dp)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Block(Modifier.width(150.dp).height(24.dp), radius = 4.dp)
            Block(Modifier.width(220.dp).height(12.dp), radius = 4.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Block(Modifier.weight(1f).height(46.dp), radius = 12.dp)
                Block(Modifier.weight(1f).height(46.dp), radius = 12.dp)
            }
        }
    }
}

@Composable
private fun TabletHeroSkeleton(gutter: Dp, viewportHeight: Dp?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val (height, short) = tabletHeroFrame(maxWidth - gutter * 2, viewportHeight)
        Box(Modifier.fillMaxWidth().padding(horizontal = gutter).height(height).testTag(TABLET_HERO_FRAME_TAG)) {
            Block(Modifier.fillMaxSize(), radius = 12.dp)
            Column(Modifier.align(Alignment.BottomStart).padding(start = if (short) 20.dp else 32.dp, end = if (short) 20.dp else 32.dp,
                bottom = if (short) 16.dp else 40.dp).widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp)) {
                Block(Modifier.width(if (short) 200.dp else 300.dp).height(if (short) 26.dp else 32.dp), radius = 4.dp)
                Block(Modifier.width(220.dp).height(14.dp), radius = 4.dp)
                Block(Modifier.fillMaxWidth().height(14.dp), radius = 4.dp)
                if (!short) Block(Modifier.fillMaxWidth(0.6f).height(14.dp), radius = 4.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Block(Modifier.width(160.dp).height(48.dp), radius = 12.dp)
                    Block(Modifier.size(48.dp), radius = 24.dp)
                    Block(Modifier.size(48.dp), radius = 24.dp)
                }
            }
        }
    }
}

@Composable
private fun DesktopHeroSkeleton(browser: Boolean, gutter: Dp) {
    Row(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = if (browser) 0.dp else 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 48.dp).widthIn(max = DesktopBreakpoints.heroSummaryMax),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Block(Modifier.width(80.dp).height(13.dp), radius = 4.dp)
            Block(Modifier.width(220.dp).height(36.dp), radius = 4.dp)
            Block(Modifier.width(180.dp).height(14.dp), radius = 4.dp)
            Block(Modifier.fillMaxWidth().height(14.dp), radius = 4.dp)
            Block(Modifier.fillMaxWidth(0.7f).height(14.dp), radius = 4.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Block(Modifier.width(120.dp).height(44.dp), radius = 12.dp)
                Block(Modifier.size(44.dp), radius = 22.dp)
                Block(Modifier.size(44.dp), radius = 22.dp)
            }
        }
        Block(Modifier.width(DesktopBreakpoints.heroArtMax).aspectRatio(16f / 9), radius = 12.dp)
    }
}

@Composable
private fun SkeletonShelf(portrait: Boolean, compact: Boolean, tablet: Boolean, gutter: Dp) {
    val cardWidth = when {
        compact -> if (portrait) 112 else 240
        tablet -> if (portrait) 164 else 300
        else -> if (portrait) 176 else 280
    }
    val cardHeight = (cardWidth * (if (portrait) 1.5f else 0.5625f)).toInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Block(Modifier.padding(horizontal = gutter).width((cardWidth * 0.9f).toInt().dp).height(20.dp), radius = 4.dp)
        Row(Modifier.padding(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(if (tablet) 14.dp else 12.dp)) {
            repeat(4) { Block(Modifier.width(cardWidth.dp).height(cardHeight.dp), radius = 8.dp) }
        }
    }
}