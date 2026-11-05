package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.ClampedText
import org.viptv.app.components.HomeFilters
import org.viptv.app.components.LocalHeaderPills
import org.viptv.app.components.MediaMetadata
import org.viptv.app.format.providerText
import org.viptv.app.components.TabletMinSide
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.state.Screen
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.saveActionName
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.app.theme.Scrim
import org.viptv.engine.wire.Card
import org.viptv.app.components.Button
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending

internal const val TABLET_HERO_FRAME_TAG = "tablet-hero-frame"

internal val TabletHeroPortraitHeight = 640.dp

internal val TabletHeroLandscapeHeight = 520.dp

internal val TabletHeroShortMin = 240.dp

internal data class TabletHeroFrame(val height: Dp, val short: Boolean)

internal fun tabletHeroFrame(contentWidth: Dp, viewportHeight: Dp?): TabletHeroFrame {
    val frame = if (contentWidth < 900.dp) TabletHeroPortraitHeight else TabletHeroLandscapeHeight
    val short = viewportHeight?.takeIf { it < TabletMinSide }
    return TabletHeroFrame(short?.coerceIn(TabletHeroShortMin, frame) ?: frame, short != null)
}

@Composable
internal fun TabletHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, navigate: (Screen) -> Unit, save: (Card) -> Unit, saved: Boolean,
    carousel: HeroCarousel? = null, viewportHeight: Dp? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val (height, short) = tabletHeroFrame(maxWidth, viewportHeight)
        Box(Modifier.fillMaxWidth().height(height).testTag(TABLET_HERO_FRAME_TAG)) {
            val art = card.landscape ?: card.poster
            if (art == null) HeroArtworkFallback(Modifier.fillMaxSize())
            else BufferedArtwork(art, Modifier.fillMaxSize(), onReady = { carousel?.artworkReady(it) })
            Box(Modifier.fillMaxSize().background(Scrim.tabletHeroWide))
            Box(Modifier.fillMaxSize().background(Scrim.tabletHeroTall))
            if (!LocalHeaderPills.current) Box(Modifier.align(Alignment.TopStart).padding(start = if (short) 20.dp else 32.dp, top = if (short) 16.dp else 40.dp)) {
                HomeFilters(Screen.Home, navigate)
            }
            Column(Modifier.fillMaxHeight().padding(start = if (short) 20.dp else 32.dp, end = if (short) 20.dp else 32.dp, bottom = if (short) 16.dp else 40.dp)
                .widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp)) {
                HeroCopyFade(card, Modifier.fillMaxWidth().weight(1f), Alignment.BottomStart) { shown ->
                    Column(verticalArrangement = Arrangement.spacedBy(if (short) 8.dp else 12.dp)) {
                        if (shown.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(shown.logo), shown.title,
                            Modifier.width(if (short) 200.dp else 300.dp).height(if (short) 88.dp else 130.dp),
                            alignment = Alignment.CenterStart, contentScale = ContentScale.Fit)
                        else Text(shown.title, fontSize = if (short) 26.sp else 32.sp, lineHeight = if (short) 30.sp else 38.sp, fontWeight = FontWeight.Bold)
                        MediaMetadata(shown)
                        shown.description.providerText()?.let { ClampedText(it, fontSize = if (short) 15.sp else 16.sp, lineHeight = if (short) 21.sp else 23.sp,
                            color = Palette.body, maxLines = if (short) 1 else 2) }
                        Row(Modifier.padding(top = if (short) 2.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { play(shown) }, modifier = Modifier.width(160.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
                                if (shown.isPlayPending()) PendingActionSpinner(Modifier.size(16.dp), size = 16.dp, strokeWidth = 2.dp, color = Palette.background)
                                else Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            val mutations = LocalPendingMutation.current
                            val target = saveTarget(shown)
                            FilledIconButton(onClick = { save(shown) }, modifier = Modifier.size(48.dp).saveActionName(saved),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Palette.text)) {
                                if (mutations.isPending(target)) MutationSpinner(24.dp)
                                else Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(24.dp))
                            }
                            FilledIconButton(onClick = { open(shown) }, modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .12f), contentColor = Palette.text)) {
                                Icon(Lucide.Info, "More info", Modifier.size(24.dp))
                            }
                        }
                    }
                }
                carousel?.let { HeroIndicators(it.count, it.index, it.progress) }
            }
        }
    }
}
