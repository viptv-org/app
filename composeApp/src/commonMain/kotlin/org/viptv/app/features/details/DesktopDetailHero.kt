package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import org.viptv.app.components.presentationBadges
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton
import org.viptv.app.components.IconButton

@Composable
internal fun DesktopDetailHero(card: Card, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean,
    playFromStart: ((Card) -> Unit)? = null, chooseSource: (Card) -> Unit = play, sourceLabel: String? = null,
    resumeEpisode: Card? = null) {
    val art = card.landscape ?: card.poster
    Box(Modifier.fillMaxWidth()) {
        art?.let { url ->
            AsyncImage(url, null, Modifier.matchParentSize().blur(60.dp), contentScale = ContentScale.Crop)
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color(0x8C0B0B0C), Color(0xB30B0B0C), Palette.background))))
        Row(Modifier.padding(horizontal = 40.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DesktopTitleArt(card)
                DesktopMetaLine(card)
                card.description?.let { Text(it, fontSize = 15.sp, lineHeight = (15 * 1.45).sp, color = Palette.body,
                    maxLines = 3, overflow = TextOverflow.Ellipsis) }
                DesktopHeroActions(card, resumeEpisode, play, save, saved, playFromStart, chooseSource, sourceLabel)
            }
            art?.let { url ->
                Box(Modifier.width(682.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    .background(Palette.surface)) {
                    AsyncImage(url, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
private fun DesktopTitleArt(card: Card) {
    if (card.logo != null) {
        AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title,
            Modifier.widthIn(max = 320.dp).heightIn(max = 132.dp), contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart)
    } else Text(card.title, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold,
        color = Palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopMetaLine(card: Card) {
    val metadata = card.metadata
    val parts = listOfNotNull(
        card.identity.kind.replaceFirstChar { it.uppercase() },
        metadata?.year?.toString(),
        metadata?.genres?.takeIf { it.isNotEmpty() }?.joinToString(", "),
        metadata?.rating?.let { rating -> "IMDb ${kotlin.math.round(rating * 10) / 10.0}" },
    )
    FlowRow(Modifier.wrapContentWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text("·", color = Palette.tertiary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(part, color = Palette.body, fontSize = 14.sp, fontWeight = FontWeight.Medium, softWrap = false)
        }
        card.presentationBadges().forEach { DesktopMetaChip(it) }
    }
}

@Composable
private fun DesktopMetaChip(value: String) {
    Box(Modifier.height(19.dp).padding(horizontal = 2.dp)) {
        Text(value, Modifier.wrapContentSize(Alignment.Center).height(19.dp).padding(horizontal = 4.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.02.sp, color = Palette.body)
    }
}

@Composable
private fun DesktopHeroActions(card: Card, resumeEpisode: Card?, play: (Card) -> Unit, save: (Card) -> Unit, saved: Boolean,
    playFromStart: ((Card) -> Unit)?, chooseSource: (Card) -> Unit, sourceLabel: String?) {
    val resumeCard = resumeEpisode ?: card.takeIf(::resuming)
    val progress = resumeCard?.progress
    val partway = resuming(resumeCard)
    val remaining = if (partway) remainingMinutes(resumeCard ?: card) else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Button(onClick = { play(resumeCard ?: card) }, modifier = Modifier.width(250.dp).height(44.dp),
                    shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Color(0xFF111113))) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Play, null, Modifier.size(15.dp))
                        Text(heroPlayLabel(resumeCard),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (remaining != null) {
                            Spacer(Modifier.weight(1f))
                            Text(remaining, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111113).copy(alpha = 0.7f))
                        }
                    }
                }
                if (partway) {
                    val fraction = progress!!.toFloat().coerceIn(0.02f, 0.98f)
                    Box(Modifier.width(250.dp).padding(horizontal = 15.dp).align(Alignment.BottomStart)
                        .height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x2E111113))) {
                        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(0xFF111113)))
                    }
                }
            }
            IconButton(onClick = { save(card) }, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.12f))) {
                Icon(if (saved) Lucide.Check else Lucide.Plus,
                    if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp), tint = Palette.text)
            }
            if (partway && playFromStart != null) {
                TextButton(onClick = { playFromStart(resumeCard ?: card) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Play from the beginning", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row(Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f))
            .pointerClickable(shape = RoundedCornerShape(10.dp)) { chooseSource(resumeCard ?: card) }.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sourceLabel ?: "Best match", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
            Icon(Lucide.ChevronDown, "Choose source", Modifier.size(16.dp), tint = Palette.secondary)
        }
    }
}

private fun remainingMinutes(card: Card): String? {
    val progress = card.progress ?: 0.0
    val runtime = card.metadata?.runtime_minutes ?: return null
    val remaining = kotlin.math.round((1.0 - progress) * runtime)
    return if (remaining > 0) "${remaining}m left" else "Almost done"
}
