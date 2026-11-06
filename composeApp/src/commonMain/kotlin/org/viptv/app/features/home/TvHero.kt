package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.*
import org.viptv.app.components.TvMediaMetadata
import org.viptv.app.components.TvAction
import org.viptv.app.components.PendingActionSpinner
import org.viptv.app.components.isPlayPending
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationFailure
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.saveActionName
import org.viptv.app.state.saveTarget
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvHero(card: Card, open: (Card) -> Unit, play: (Card) -> Unit, first: FocusRequester? = null,
    down: FocusRequester? = null, indicators: Int = 1, selected: Int = 0, progress: HeroProgress? = null, focusEpoch: Int = 0, requestFocus: Boolean = false,
    save: (Card) -> Unit = {}, saved: Boolean = false, isSaved: (Card) -> Boolean = { saved }) {
    org.viptv.app.components.TvRequestedFocus(first, focusEpoch, requestFocus)
    var presented by remember { mutableStateOf(card) }
    val presentedSaved = isSaved(presented)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = maxWidth / 1920.dp
        // Bottom-anchored: a larger font must grow the copy up into the artwork, not down into the shelves below the billboard.
        Box(Modifier.fillMaxWidth().heightIn(min = 800.dp * scale)) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 96.dp * scale, bottom = 16.dp * scale)) {
        org.viptv.app.components.FadeThrough(card, Modifier.width(1100.dp * scale), contentKey = { it.identity }) {
            SideEffect { presented = it }
            TvHeroSummary(it, scale)
        }
        Spacer(Modifier.height(28.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            // The hero's leading edge dead-ends Left: geometric search would otherwise reach the profile avatar in the nav.
            TvAction(Modifier.width(220.dp * scale).heightIn(min = 72.dp * scale).focusProperties { if (down != null) this.down = down; left = FocusRequester.Cancel }, scale, ring = false,
                primary = true, requester = first, activate = { play(presented) }, contentPadding = PaddingValues(horizontal = 28.dp * scale), arrangement = Arrangement.Start) {
                if (presented.isPlayPending()) PendingActionSpinner(Modifier.size(24.dp * scale), size = 24.dp * scale, strokeWidth = 3.dp * scale, color = it)
                else Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), it)
                Spacer(Modifier.width(12.dp * scale))
                Text("Play", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            val mutations = LocalPendingMutation.current
            val target = saveTarget(presented)
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }.saveActionName(presentedSaved),
                scale, round = true, ring = false, activate = { save(presented) }) {
                if (mutations.isPending(target)) MutationSpinner(30.dp, scale = scale, strokeWidth = 3.dp)
                else Icon(if (presentedSaved) Lucide.Check else Lucide.Plus, null, Modifier.size(30.dp * scale), it)
            }
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, round = true, ring = false, activate = { open(presented) }) { Icon(Lucide.Info, "More info", Modifier.size(30.dp * scale), it) }
        }
        MutationFailure(saveTarget(presented), Modifier.padding(top = 12.dp * scale), scale = 2f * scale)
        Spacer(Modifier.height(40.dp * scale))
        progress?.let { HeroIndicators(indicators, selected, it, Modifier, scale, animated = false) }
        }
        }
    }
}
