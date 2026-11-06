package org.viptv.app.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The inset every TV page keeps inside the 1920x1080 reference: 96px sides and 72px above its first row.
internal val TvPageSideInset = 96.dp
internal val TvPageTopInset = 72.dp

internal fun Modifier.tvPagePadding(scale: Float): Modifier =
    padding(start = TvPageSideInset * scale, end = TvPageSideInset * scale, top = TvPageTopInset * scale)