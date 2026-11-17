package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.FillerKind
import org.viptv.app.state.EpisodeFillerLabels
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal val LocalEpisodeFiller = staticCompositionLocalOf { EpisodeFillerLabels.Empty }

internal fun FillerKind.label(): String = when (this) {
    FillerKind.Filler -> "Filler"
    FillerKind.Mixed -> "Mixed"
}

internal fun FillerKind.spoken(): String = when (this) {
    FillerKind.Filler -> "Filler episode"
    FillerKind.Mixed -> "Mixed canon and filler episode"
}

@Composable
internal fun BoxScope.EpisodeFillerTag(card: Card, television: Boolean = false, scale: Float = 1f) {
    val kind = LocalEpisodeFiller.current.of(card) ?: return
    val unit = if (television) scale else 1f
    Text(kind.label(), Modifier.align(Alignment.TopEnd).padding((if (television) 12 else 6).dp * unit)
        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp * unit))
        .padding(horizontal = (if (television) 10 else 6).dp * unit, vertical = (if (television) 4 else 2).dp * unit)
        .clearAndSetSemantics { contentDescription = kind.spoken() },
        color = Palette.secondary, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        fontSize = (if (television) 18 * scale else 11f).sp, lineHeight = (if (television) 22 * scale else 14f).sp)
}
