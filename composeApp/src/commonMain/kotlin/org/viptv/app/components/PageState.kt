package org.viptv.app.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

/** One way out of a page state: the primary fixes the state, the secondary is the other way out. */
internal class PageStateAction(val label: String, val primary: Boolean = false, val icon: ImageVector? = null,
    val activate: () -> Unit)

internal enum class PageStateLayout { Phone, Wide, Tv }

internal const val PAGE_STATE_TAG = "page-state"

internal fun pageStateLayout(television: Boolean, compact: Boolean): PageStateLayout =
    if (television) PageStateLayout.Tv else if (compact) PageStateLayout.Phone else PageStateLayout.Wide

/** The block a list page draws when it has nothing to show, or when its first request failed. */
@Composable
internal fun PageState(icon: ImageVector, title: String, message: String, actions: List<PageStateAction>,
    modifier: Modifier = Modifier, layout: PageStateLayout = PageStateLayout.Phone, tile: Boolean = true,
    scale: Float = 1f, focus: FocusRequester? = null, enabled: Boolean = true) {
    val television = layout == PageStateLayout.Tv
    if (television) TvRequestedFocus(focus, title, enabled)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = when (layout) { PageStateLayout.Tv -> 980.dp * scale; PageStateLayout.Wide -> 620.dp; PageStateLayout.Phone -> 440.dp })
            .testTag(PAGE_STATE_TAG)
            .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(when (layout) { PageStateLayout.Tv -> 18.dp * scale; PageStateLayout.Wide -> 10.dp; PageStateLayout.Phone -> 12.dp })) {
            if (television) Box(Modifier.size(96.dp * scale).clip(CircleShape).background(Color.White.copy(alpha = .1f)),
                contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(46.dp * scale), tint = Palette.text) }
            else if (tile) Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Palette.raised),
                contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(34.dp), tint = Palette.secondary) }
            else Icon(icon, null, Modifier.size(30.dp), tint = Palette.text)
            Text(title, Modifier.semantics { heading() }, fontSize = (if (television) 44 * scale else 22f).sp,
                lineHeight = (if (television) 50 * scale else 25f).sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (message.isNotBlank()) Text(message, Modifier.widthIn(max = if (layout == PageStateLayout.Wide) 440.dp else Dp.Unspecified),
                color = Palette.secondary,
                fontSize = (when (layout) { PageStateLayout.Tv -> 26 * scale; PageStateLayout.Wide -> 14f; PageStateLayout.Phone -> 15f }).sp,
                lineHeight = (when (layout) { PageStateLayout.Tv -> 38 * scale; PageStateLayout.Wide -> 20f; PageStateLayout.Phone -> 22f }).sp,
                textAlign = TextAlign.Center)
            if (actions.isNotEmpty()) {
                val gap = when (layout) { PageStateLayout.Tv -> 16.dp * scale; PageStateLayout.Wide -> 8.dp; PageStateLayout.Phone -> 12.dp }
                val block = Modifier.padding(top = when (layout) { PageStateLayout.Tv -> 18.dp * scale; PageStateLayout.Wide -> 6.dp; PageStateLayout.Phone -> 0.dp })
                if (television || layout == PageStateLayout.Wide) Row(block, horizontalArrangement = Arrangement.spacedBy(gap)) {
                    actions.forEachIndexed { index, action ->
                        PageStateActionButton(action, layout, scale, if (index == 0) focus else null, enabled)
                    }
                } else Column(block, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                    actions.forEach { PageStateActionButton(it, layout, scale, null, enabled) }
                }
            }
        }
    }
}

@Composable
private fun PageStateActionButton(action: PageStateAction, layout: PageStateLayout, scale: Float,
    requester: FocusRequester?, enabled: Boolean) {
    if (layout == PageStateLayout.Tv) {
        val primary = action.primary
        TvAction(Modifier.heightIn(min = 72.dp * scale).widthIn(min = (if (primary) 300 else 260).dp * scale), scale,
            requester = requester, enabled = enabled, activate = action.activate,
            contentPadding = PaddingValues(horizontal = 24.dp * scale)) { ink ->
            action.icon?.let { Icon(it, null, Modifier.size(28.dp * scale), tint = ink) }
            Text(action.label, color = ink, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val phone = layout == PageStateLayout.Phone
    Button(action.activate, Modifier.height(if (phone) 44.dp else 38.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = if (action.primary) null else BorderStroke(1.dp, Palette.text.copy(alpha = .14f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (action.primary) Palette.text else Color.Transparent,
            contentColor = if (action.primary) Palette.background else Palette.text),
        contentPadding = PaddingValues(horizontal = if (phone) 18.dp else 16.dp)) {
        action.icon?.let { Icon(it, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(action.label, fontSize = (if (phone) 15 else 14).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}