@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.components

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal val LocalTvFocusAnchoring = staticCompositionLocalOf { false }

/** Distances are in viewport coordinates; Foundation owns animation and scroll-limit clamping. */
internal class LeadingFocusScroll(private val inset: Float, private val fromEnd: Boolean = false) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (fromEnd) offset + size - (containerSize - inset) else offset - inset

    fun logicalScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        calculateScrollDistance(offset, size, containerSize) * if (fromEnd) -1f else 1f
}

/** A section list has one explicit owner; nested collections install their own native policy. */
private object SectionFocusScroll : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@Composable
internal fun TvFocusEnvironment(television: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvFocusAnchoring provides television, content = content)
}

@Composable
internal fun TvFocusScroll(
    horizontal: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    sectionControlled: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!LocalTvFocusAnchoring.current) { content(); return }
    val direction = LocalLayoutDirection.current
    val fromEnd = reverseLayout xor (horizontal && direction == LayoutDirection.Rtl)
    val inset = with(LocalDensity.current) {
        (if (horizontal) {
            if (fromEnd) contentPadding.calculateRightPadding(direction) else contentPadding.calculateLeftPadding(direction)
        } else if (fromEnd) contentPadding.calculateBottomPadding() else contentPadding.calculateTopPadding()).toPx()
    }
    val spec = remember(inset, fromEnd, sectionControlled) {
        if (sectionControlled) SectionFocusScroll else LeadingFocusScroll(inset, fromEnd)
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
