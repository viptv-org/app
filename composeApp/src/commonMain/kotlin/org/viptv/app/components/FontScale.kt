package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A rail or sidebar holding labels has to widen with the system font scale, or a single word breaks mid-word.
@Composable
internal fun textChromeWidth(base: Dp, cap: Float = 1.6f): Dp =
    base * LocalDensity.current.fontScale.coerceIn(1f, cap)
