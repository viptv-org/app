package org.viptv.app.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import org.viptv.app.components.playerDensity

@Composable
internal fun PlayerOverlayHost(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalDensity provides playerDensity()) { Box(modifier, content = content) }
}
