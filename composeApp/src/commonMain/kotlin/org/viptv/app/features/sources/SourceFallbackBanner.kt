package org.viptv.app.features.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette

@Composable
internal fun SourceFallbackBanner(message: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }.background(Palette.raised, RoundedCornerShape(10.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Lucide.CircleAlert, null, Modifier.size(fontSize.value.dp * 1.6f), tint = Palette.danger)
        Text(message, color = Palette.text, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}
