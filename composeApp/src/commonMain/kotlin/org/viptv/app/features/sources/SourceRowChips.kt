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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Captions
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Volume2
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
internal fun SourceAudioSubsLine(source: SourceCandidate, color: Color, sectionFontSize: TextUnit, chipFontSize: TextUnit,
    iconSize: Dp, padH: Dp, chipHeight: Dp, gap: Dp, outline: Dp, radius: Dp) {
    val audio = source.audioChips()
    val subs = source.subtitleChips()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (audio == null && subs == null) {
            Text("Audio not listed", Modifier.height(chipHeight), fontSize = sectionFontSize, color = color.copy(alpha = .55f))
            return@FlowRow
        }
        if (audio != null) {
            SourceSectionLabel(Lucide.Volume2, "Audio", color, sectionFontSize, iconSize, chipHeight)
            audio.forEach { SourceLanguageChip(it.label, color, it.dashed, chipFontSize, padH, chipHeight, outline, radius) }
        }
        if (subs != null) {
            SourceSectionLabel(Lucide.Captions, "Subs", color, sectionFontSize, iconSize, chipHeight)
            subs.forEach { SourceLanguageChip(it.label, color, it.dashed, chipFontSize, padH, chipHeight, outline, radius) }
        } else if (audio != null) {
            Text("Subs not listed", Modifier.height(chipHeight), fontSize = sectionFontSize, color = color.copy(alpha = .55f))
        }
    }
}

@Composable
internal fun SourceFactsRow(source: SourceCandidate, addon: String?, color: Color, fontSize: TextUnit, iconSize: Dp) {
    val facts = source.factItems(addon)
    if (facts.isEmpty()) return
    val size = source.sizeLabel()
    val packageId = "season-pack"
    val placeholder = with(LocalDensity.current) { iconSize.toSp() }
    val inline = mapOf(
        packageId to InlineTextContent(
            Placeholder(width = placeholder, height = placeholder, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
        ) { Icon(Lucide.Package, null, Modifier.size(iconSize), tint = color.copy(alpha = .75f)) },
    )
    val text = buildAnnotatedString {
        facts.forEachIndexed { index, item ->
            if (index > 0) append(" · ")
            when {
                item == SEASON_PACK_LABEL -> { appendInlineContent(packageId, "Season pack"); append(" Season pack") }
                item == size -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(item) }
                else -> append(item)
            }
        }
    }
    Text(text, inlineContent = inline, fontSize = fontSize, color = color.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SourceSectionLabel(icon: ImageVector, text: String, color: Color, fontSize: TextUnit, iconSize: Dp, chipHeight: Dp) {
    Box(Modifier.height(chipHeight), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(iconSize), tint = color.copy(alpha = .6f))
            Text(text, fontSize = fontSize, color = color.copy(alpha = .6f), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SourceLanguageChip(text: String, color: Color, dashed: Boolean, fontSize: TextUnit, padH: Dp, chipHeight: Dp, outline: Dp, radius: Dp) {
    Box(Modifier.height(chipHeight).chipOutline(color.copy(alpha = .5f), outline, radius, dashed).padding(horizontal = padH), contentAlignment = Alignment.Center) {
        Text(text, fontSize = fontSize, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

private fun Modifier.chipOutline(color: Color, width: Dp, radius: Dp, dashed: Boolean): Modifier =
    if (!dashed) this.border(width, color, RoundedCornerShape(radius))
    else this.drawBehind {
        val stroke = width.toPx()
        val inset = stroke / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)),
        )
    }