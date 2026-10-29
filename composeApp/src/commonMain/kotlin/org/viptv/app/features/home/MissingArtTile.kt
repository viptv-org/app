package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette
import org.viptv.app.theme.viptvDisplayFamily

internal const val MISSING_ART_TAG = "missing-art"

@Composable
internal fun MissingArtTile(title: String, scale: Float = 1f, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()
        // Without this a card reads its title twice: once on the art, once in its caption.
        .clearAndSetSemantics { testTag = MISSING_ART_TAG; contentDescription = title }
        .background(Palette.raised).padding(12.dp * scale),
        verticalArrangement = Arrangement.spacedBy(10.dp * scale, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Lucide.Film, null, Modifier.size(24.dp * scale), tint = Palette.tertiary)
        Text(title, color = Palette.secondary, textAlign = TextAlign.Center,
            fontFamily = viptvDisplayFamily(), fontWeight = FontWeight.ExtraBold,
            fontSize = (16 * scale).sp, lineHeight = (19.2f * scale).sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}