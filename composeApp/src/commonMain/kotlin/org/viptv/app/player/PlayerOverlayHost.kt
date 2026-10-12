package org.viptv.app.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun PlayerOverlayHost(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, content = content)
}
