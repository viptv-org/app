package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.FocusOutline
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerHovered
import org.viptv.app.state.RowSeeAll
import org.viptv.app.state.titleCount
import org.viptv.app.theme.Palette

internal fun seeAllKey(rowId: String) = "$rowId:see-all"

internal fun seeAllDescription(seeAll: RowSeeAll): String =
    "See all ${seeAll.title}" + (seeAll.count?.let { ", ${titleCount(it)}" } ?: "")

@Composable
internal fun SeeAllCard(seeAll: RowSeeAll, width: Int, open: () -> Unit, portrait: Boolean = false, labelScale: Float = 1f,
    cornerRadius: Dp = 8.dp, focusWidth: Dp = 3.dp, showFocusOutline: Boolean = true, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered = interaction.pointerHovered()
    val shape = RoundedCornerShape(cornerRadius)
    val description = seeAllDescription(seeAll)
    Column(modifier.width(width.dp).hoverable(interaction)
        .onFocusChanged { focused = it.isFocused }
        .pointerClickable(interactionSource = interaction, indication = null, role = Role.Button) { open() }
        .clearAndSetSemantics { role = Role.Button; contentDescription = description; onClick { open(); true } }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(shape)
            .background(if (hovered) Palette.raised else Palette.surface).border(1.dp, Palette.raised, shape),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp * labelScale)) {
                Box(Modifier.size(40.dp * labelScale).clip(CircleShape).background(Palette.text.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                    Icon(Lucide.ArrowRight, null, Modifier.size(20.dp * labelScale), tint = Palette.text)
                }
                Text("See all", fontSize = (14 * labelScale).sp, lineHeight = (18 * labelScale).sp, fontWeight = FontWeight.SemiBold)
            }
            if (showFocusOutline) FocusOutline(focused, cornerRadius, focusWidth, Modifier.matchParentSize())
        }
        Spacer(Modifier.height(9.dp))
        Text(seeAll.title, fontSize = (14 * labelScale).sp, lineHeight = (18 * labelScale).sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(seeAll.caption, fontSize = (12 * labelScale).sp, lineHeight = (16 * labelScale).sp, color = Palette.secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TvSeeAllCard(seeAll: RowSeeAll, scale: Float, open: () -> Unit, modifier: Modifier = Modifier,
    portrait: Boolean = false, focused: () -> Unit = {}, onUp: (() -> Unit)? = null,
    artworkWidth: Dp = (if (portrait) 255.2f else 422.4f).dp * scale) {
    val focus = remember { mutableStateOf(false) }
    val shape = remember(scale) { RoundedCornerShape(10.dp * scale) }
    val description = seeAllDescription(seeAll)
    Column(modifier.width(artworkWidth)
        .onPreviewKeyEvent { event ->
            if (onUp != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) { onUp(); true } else false
        }
        .onFocusChanged { if (focus.value != it.isFocused) { focus.value = it.isFocused; if (it.isFocused) focused() } }
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open() }
        .clearAndSetSemantics {
            role = Role.Button
            text = AnnotatedString(description)
            contentDescription = description
            set(SemanticsProperties.Focused, focus.value)
            onClick { open(); true }
        }) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (portrait) 2f / 3 else 16f / 9).clip(shape).background(Palette.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
                Box(Modifier.size(72.dp * scale).background(Palette.text.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Lucide.ArrowRight, null, Modifier.size(32.dp * scale), tint = Palette.text)
                }
                Text("See all", fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
            FocusOutline(focus.value, 10.dp * scale, 3.dp * scale, Modifier.matchParentSize())
        }
        Column(Modifier.fillMaxWidth().height(76.dp * scale).padding(top = 14.dp * scale), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(seeAll.title, fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp,
                color = if (focus.value) Palette.text else Palette.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(seeAll.caption, fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, color = Palette.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
