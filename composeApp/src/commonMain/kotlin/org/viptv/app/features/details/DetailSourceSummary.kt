package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.pointerClickable
import org.viptv.app.features.home.CHOOSE_SOURCE_LABEL
import org.viptv.app.features.sources.providerLabel
import org.viptv.app.features.sources.qualityLabel
import org.viptv.app.state.DetailSourcePreview
import org.viptv.app.state.SOURCES_EMPTY_LABEL
import org.viptv.app.state.SOURCES_UNAVAILABLE_LABEL
import org.viptv.app.theme.Palette

internal val PhoneSourceSummaryHeight = 58.dp

internal data class SourceSummaryFacts(val quality: String, val provider: String, val count: String)

// AND-043: the phone source row keeps its 58dp height; activating it opens the picker.
internal fun DetailSourcePreview.summaryFacts(): SourceSummaryFacts? {
    val best = best ?: return null
    return SourceSummaryFacts(best.qualityLabel() ?: "Auto", best.providerLabel(), sourceCountLabel)
}

@Composable
internal fun DetailSourceSummary(preview: DetailSourcePreview?, open: () -> Unit, modifier: Modifier = Modifier) {
    val facts = preview?.summaryFacts()
    Row(modifier.fillMaxWidth().height(PhoneSourceSummaryHeight)
        .pointerClickable(role = Role.Button, onClick = open).semantics { contentDescription = CHOOSE_SOURCE_LABEL }
        .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            facts != null -> {
                SourceQualityBadge(facts.quality)
                Text("${facts.provider} · ${facts.count}", fontSize = 13.sp, lineHeight = 18.sp,
                    color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            preview?.failed == true -> Text(SOURCES_UNAVAILABLE_LABEL, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.secondary)
            preview?.settled == true -> Text(SOURCES_EMPTY_LABEL, fontSize = 13.sp, lineHeight = 18.sp, color = Palette.secondary)
            else -> {
                SkeletonBlock(38.dp, 17.dp)
                SkeletonBlock(142.dp, 14.dp)
            }
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.tertiary)
    }
}

@Composable
private fun SourceQualityBadge(quality: String) {
    Box(Modifier.height(17.dp).border(1.dp, Palette.secondary.copy(alpha = .5f), RoundedCornerShape(4.dp))
        .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(quality, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = .02.sp, fontWeight = FontWeight.Bold,
            color = Palette.body, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SkeletonBlock(width: Dp, height: Dp) {
    Box(Modifier.width(width).height(height).background(Palette.raised, RoundedCornerShape(3.dp)))
}