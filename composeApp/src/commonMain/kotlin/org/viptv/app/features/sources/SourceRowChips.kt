package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.SourceCandidate

@Composable
internal fun SourceCacheChip(cache: CacheChip, fontSize: TextUnit, iconSize: Dp, padH: Dp, padV: Dp, radius: Dp, gap: Dp) {
    val tint = when (cache.kind) {
        CacheKind.Instant -> Palette.segmentCredits
        CacheKind.NotCached -> Palette.accent
    }
    val text = when (cache.kind) {
        CacheKind.Instant -> "⚡ Instant" + (cache.service?.let { " · $it" } ?: "")
        CacheKind.NotCached -> "Not cached" + (cache.service?.let { " · $it" } ?: "")
    }
    Row(Modifier.background(tint.copy(alpha = .18f), RoundedCornerShape(radius)).padding(horizontal = padH, vertical = padV),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (cache.kind == CacheKind.NotCached) Icon(Lucide.CloudOff, null, Modifier.size(iconSize), tint = tint)
        Text(text, fontSize = fontSize, color = tint, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun SourceTagLine(source: SourceCandidate, color: Color, fontSize: TextUnit, chipHeight: Dp, padH: Dp, gap: Dp, outline: Dp, radius: Dp) {
    val tags = source.sourceTags()
    val size = source.sizeLabel()
    Box(Modifier.height(chipHeight)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
            itemVerticalAlignment = Alignment.CenterVertically) {
            tags.forEach { tag ->
                Box(Modifier.height(chipHeight).border(outline, color.copy(alpha = .5f), RoundedCornerShape(radius)).padding(horizontal = padH),
                    contentAlignment = Alignment.Center) {
                    Text(tag, fontSize = fontSize, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
            size?.let { Text(it, fontSize = fontSize, color = color.copy(alpha = .75f), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) }
        }
    }
}

