package org.viptv.app.features.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.viptv.engine.wire.Card

internal const val DETAIL_TITLE_TAG = "detail-title"

// A text title takes as many lines as it wraps to, so a header hands the logo's height over as a minimum, never a maximum.
@Composable
internal fun DetailHeroTitle(card: Card, style: TextStyle, modifier: Modifier = Modifier, logo: Modifier = Modifier,
    reserve: Dp? = null, alignment: Alignment = Alignment.CenterStart, maxLines: Int = Int.MAX_VALUE) {
    val area = if (reserve != null) modifier.heightIn(min = reserve) else modifier
    Box(area.testTag(DETAIL_TITLE_TAG), contentAlignment = alignment) {
        val source = card.logo
        if (source != null) AsyncImage(org.viptv.app.images.rememberLogoRequest(source), card.title, logo,
            alignment = alignment, contentScale = ContentScale.Fit)
        else Text(card.title, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
