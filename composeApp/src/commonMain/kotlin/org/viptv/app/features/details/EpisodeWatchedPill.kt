package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.WATCHED_BADGE
import org.viptv.app.components.isWatched
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal fun Card.watchedEpisode(): Boolean = isWatched()

internal fun Card.episodeArtLabel(): String? =
    badges.firstOrNull { !it.equals("Simkl", true) && !it.equals(WATCHED_BADGE, true) }

@Composable
internal fun EpisodeWatchedPill(television: Boolean, scale: Float = 1f, modifier: Modifier = Modifier) {
    val unit = if (television) scale else 1f
    Row(modifier.height((if (television) 32 else 24).dp * unit)
        .background(Color(0xFF2A2A2E), RoundedCornerShape(50))
        .padding(horizontal = 8.dp * unit),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp * unit)) {
        Icon(Lucide.Check, null, Modifier.size((if (television) 20 else 14).dp * unit), tint = Palette.accent)
        Text(WATCHED_BADGE, color = Palette.text, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            fontSize = (if (television) 18 * scale else 11f).sp, lineHeight = (if (television) 22 * scale else 14f).sp)
    }
}
