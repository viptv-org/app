package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// A rail or sidebar holding labels has to widen with the system font scale, or a single word breaks mid-word.
@Composable
internal fun textChromeWidth(base: Dp, cap: Float = 1.6f): Dp =
    base * LocalDensity.current.fontScale.coerceIn(1f, cap)

// A column that holds one short string (a time, a channel number) tracks the font scale exactly: nothing shares it, so it
// can take the width its text needs instead of splitting it.
@Composable
internal fun textColumnWidth(base: Dp): Dp = base * LocalDensity.current.fontScale

// Artwork beside a row's text gives up room as the font scale grows: the text still needs the width of one word.
@Composable
internal fun artworkBesideText(base: Dp, cap: Float = 1.6f): Dp =
    base / LocalDensity.current.fontScale.coerceIn(1f, cap)
