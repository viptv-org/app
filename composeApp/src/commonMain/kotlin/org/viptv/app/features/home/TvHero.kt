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
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
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
        Box(Modifier.fillMaxWidth().height(800.dp * scale)) {
        org.viptv.app.components.FadeThrough(card, Modifier.width(1100.dp * scale).height(630.dp * scale)) {
            SideEffect { presented = it }
            TvHeroSummary(it, scale)
        }
        Row(Modifier.offset(96.dp * scale, 648.dp * scale), horizontalArrangement = Arrangement.spacedBy(20.dp * scale)) {
            TvAction(Modifier.width(220.dp * scale).height(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, ring = false,
                primary = true, requester = first, activate = { play(presented) }, contentPadding = PaddingValues(horizontal = 28.dp * scale), arrangement = Arrangement.Start) {
                Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(24.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text("Play", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            }
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, round = true, ring = false, activate = { save(presented) }) { Icon(if (presentedSaved) Lucide.Check else Lucide.Plus, if (presentedSaved) "Remove from My List" else "Add to My List", Modifier.size(30.dp * scale), it) }
            TvAction(Modifier.size(72.dp * scale).focusProperties { if (down != null) this.down = down }, scale, round = true, ring = false, activate = { open(presented) }) { Icon(Lucide.Info, "More info", Modifier.size(30.dp * scale), it) }
        }
        progress?.let { HeroIndicators(indicators, selected, it, Modifier.offset(96.dp * scale, 760.dp * scale), scale) }

        }
    }
}
