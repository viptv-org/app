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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import com.composables.icons.lucide.*
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.saveActionName
import org.viptv.app.components.TvAction
import org.viptv.app.components.TitleLogo
import org.viptv.app.components.tvHold
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.state.saveTarget
import org.viptv.app.format.providerText
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun TvDetailHero(card: Card, extras: TvDetailExtras, scale: Float, play: () -> Unit,
    save: () -> Unit, trailer: () -> Unit, restart: (() -> Unit)?, first: FocusRequester, down: FocusRequester?, requestInitialFocus: Boolean,
    saved: Boolean = false, downAction: (() -> Unit)? = null, hold: (() -> Unit)? = null) {
    val series = card.identity.kind == "series"
    val pending = (extras.resume ?: card).isPlayPending()
    val mutations = LocalPendingMutation.current
    val mutationTarget = saveTarget(card)
    val failure = mutations.failure(mutationTarget)
    org.viptv.app.components.TvRequestedFocus(first, card.identity, requestInitialFocus)
    Column(Modifier.fillMaxWidth().heightIn(min = 816.dp * scale).clipToBounds().padding(start = 96.dp * scale).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown && downAction != null) {
            downAction(); true
        } else false
    }) {
        Spacer(Modifier.height((if (series) 250 else 220).dp * scale))
        Box(Modifier.width(800.dp * scale).heightIn(min = (if (series) 134 else 190).dp * scale), contentAlignment = Alignment.BottomStart) {
            TitleLogo(card.logo, card.title,
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
                logoModifier = Modifier.width((if (series) 460 else 260).dp * scale).height((if (series) 134 else 190).dp * scale),
                logoAlignment = Alignment.BottomStart) {
                Text(card.title, fontSize = (52 * scale).sp, lineHeight = (60 * scale).sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height((if (series) 26 else 22).dp * scale))
        TvDetailMetadata(card, scale)
        Spacer(Modifier.height(20.dp * scale))
        Box(Modifier.width(800.dp * scale).heightIn(min = 113.dp * scale)) {
            card.description.providerText()?.let { Text(it,
                fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.body,
                maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(13.dp * scale))
        HeroStarring(card, (22 * scale).sp, (30 * scale).sp, Modifier.width(800.dp * scale))
        Spacer(Modifier.height(32.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width((if (extras.resume != null) 420 else 260).dp * scale).heightIn(min = 72.dp * scale)
                .then(if (hold != null) Modifier.tvHold(onHold = hold) else Modifier)
                .focusProperties { if (down != null) this.down = down; left = FocusRequester.Cancel },
                scale = scale, primary = true, ring = false, requester = first, activate = play,
                contentPadding = PaddingValues(horizontal = 28.dp * scale), arrangement = Arrangement.Start) { tint ->
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                        if (pending) PendingActionSpinner(Modifier.size(24.dp * scale), size = 24.dp * scale, strokeWidth = 3.dp * scale, color = tint)
                        else Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), tint)
                        Spacer(Modifier.width(12.dp * scale))
                        Text(extras.resumeLabel ?: "Play", color = tint, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                        extras.remaining?.let { Spacer(Modifier.width(54.dp * scale)); Text(it, color = tint.copy(alpha = 0.7f), fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold) }
                    }
                    extras.resume?.progress?.takeIf { resuming(extras.resume) }?.let { progress -> LinearProgressIndicator(progress = { progress.toFloat() },
                        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp * scale).fillMaxWidth().height(4.dp * scale),
                        color = tint, trackColor = tint.copy(alpha = 0.2f)) }
                }
            }
            if (failure != null) TvAction(Modifier.heightIn(min = 72.dp * scale), scale, ring = false,
                activate = { mutations.retry(mutationTarget) }, contentPadding = PaddingValues(horizontal = 24.dp * scale),
                arrangement = Arrangement.Start) { tint ->
                Icon(Lucide.RotateCcw, null, Modifier.size(28.dp * scale), tint)
                Spacer(Modifier.width(10.dp * scale))
                Text("Retry", color = tint, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            } else TvAction(Modifier.size(72.dp * scale).saveActionName(saved), scale, round = true, ring = false, activate = save) {
                if (mutations.isPending(mutationTarget)) MutationSpinner(30.dp, scale = scale, strokeWidth = 3.dp)
                else Icon(if (saved) Lucide.Check else Lucide.Plus, null, Modifier.size(30.dp * scale), it)
            }
            if (restartTarget(card, extras.resume) != null && restart != null) TvAction(Modifier.size(72.dp * scale), scale, round = true, ring = false, activate = restart) {
                Icon(Lucide.RotateCcw, RESTART_LABEL, Modifier.size(30.dp * scale), it)
            }
        }
        failure?.let {
            Spacer(Modifier.height(14.dp * scale))
            Text(it, Modifier.width(800.dp * scale), color = Palette.danger,
                fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        }
    }
}
