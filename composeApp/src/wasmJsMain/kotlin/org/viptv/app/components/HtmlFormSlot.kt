package org.viptv.app.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

// HtmlElementView lays out at the minimum constraints, so a native control without an exact width collapses to 0×N and stays hidden at 0,0.
internal fun Modifier.nativeFormSlot(height: Dp): Modifier = fillMaxWidth().height(height)