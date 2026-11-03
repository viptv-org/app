package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

// One shared backdrop for a pinned phone header over scrolling content: clear at the top,
// Palette.background once the page scrolls, with the standard edge fade fading in below it.
@Composable
internal fun PinnedHeaderSurface(
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(height)
            .background(if (scrolled) Palette.background else Color.Transparent), content = content)
        if (scrolled) ScrollEdgeFade(ScrollEdge.Top, fade = ScrollEdgeFadeHeight)
    }
}