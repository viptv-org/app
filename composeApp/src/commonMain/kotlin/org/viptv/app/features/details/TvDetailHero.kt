package org.viptv.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.TvAction
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

// DATA-03: nothing in the wire carries a trailer, so the circle is not drawn
// rather than opening a dialog that claims a title option exists. The now-unused
// parameter stays until its stub call site in TvDetailsScreen is deleted, so this
// file's fix does not break the build.
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun TvDetailHero(card: Card, extras: TvDetailExtras, scale: Float, play: () -> Unit,
    save: () -> Unit, trailer: () -> Unit, restart: () -> Unit, first: FocusRequester, down: FocusRequester?, requestInitialFocus: Boolean,
    saved: Boolean = false, downAction: (() -> Unit)? = null) {
    val series = card.identity.kind == "series"
    org.viptv.app.components.TvRequestedFocus(first, card.identity, requestInitialFocus)
    Box(Modifier.fillMaxWidth().height(816.dp * scale).clipToBounds().onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && downAction != null) {
            downAction(); true
        } else false
    }) {
        if (card.logo != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(card.logo), card.title,
            Modifier.offset(96.dp * scale, (if (series) 250 else 220).dp * scale)
                .width((if (series) 460 else 260).dp * scale).height((if (series) 134 else 190).dp * scale)
                ,
            alignment = Alignment.BottomStart, contentScale = ContentScale.Fit)
        else Box(Modifier.offset(96.dp * scale, (if (series) 250 else 220).dp * scale)
            .size(800.dp * scale, (if (series) 134 else 190).dp * scale)) {
            Text(card.title, Modifier.align(Alignment.BottomStart), fontSize = (52 * scale).sp,
                lineHeight = (60 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.offset(96.dp * scale, (if (series) 410 else 432).dp * scale).width(1100.dp * scale)) {
            TvDetailMetadata(card, scale)
        }
        card.description?.let { Text(it,
            Modifier.offset(96.dp * scale, (if (series) 460 else 482).dp * scale).width(800.dp * scale),
            fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.body,
            maxLines = 3, overflow = TextOverflow.Ellipsis) }
        extras.cast?.let { Text("Starring $it", Modifier.offset(96.dp * scale, 586.dp * scale).width(800.dp * scale),
            color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
        Row(Modifier.offset(96.dp * scale, (if (series) 648 else 668).dp * scale),
            horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width((if (extras.resume != null) 420 else 260).dp * scale).height(72.dp * scale)
                .focusProperties { if (down != null) this.down = down },
                scale = scale, primary = true, requester = first, activate = play,
                contentPadding = PaddingValues(horizontal = 28.dp * scale), arrangement = Arrangement.Start) { tint ->
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), tint)
                        Spacer(Modifier.width(12.dp * scale))
                        Text(extras.resumeLabel ?: "Play", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                        extras.remaining?.let { Spacer(Modifier.width(54.dp * scale)); Text(it, color = tint.copy(alpha = 0.7f), fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold) }
                    }
                    extras.resume?.progress?.let { progress -> LinearProgressIndicator(progress = { progress.toFloat() },
                        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp * scale).fillMaxWidth().height(4.dp * scale),
                        color = tint, trackColor = tint.copy(alpha = 0.2f)) }
                }
            }
            TvAction(Modifier.size(72.dp * scale), scale, round = true, activate = save) { Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(30.dp * scale), it) }
            if (extras.resume != null) TvAction(Modifier.size(72.dp * scale), scale, round = true, activate = restart) { Icon(Lucide.RotateCcw, "Restart", Modifier.size(30.dp * scale), it) }
        }
    }
}
