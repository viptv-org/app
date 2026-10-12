@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.components

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Preserve the composition while the focused control is already fully visible. */
private object VisibleControlScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0 && offset + size > containerSize -> 0f
        offset < 0 -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}
private object ExplicitShelfScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@Composable
internal fun TvFocusScroll(manual: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides if (manual) ExplicitShelfScroll else VisibleControlScroll, content = content)
}
