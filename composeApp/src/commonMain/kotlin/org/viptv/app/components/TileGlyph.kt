package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// A glyph that fills a fixed tile is artwork: the accessibility font scale must not grow it past the tile it sits in.
@Composable
internal fun tileGlyphSize(fraction: Float, tile: Dp): TextUnit =
    with(LocalDensity.current) { (tile.value * fraction * density).toSp() }
