package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import org.viptv.app.components.FocusOutline
import org.viptv.app.components.pointerClickable
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal class TvTitleMenuActions(
    val play: (Card) -> Unit,
    val info: (Card) -> Unit,
    val save: ((Card) -> Unit)? = null,
    val saved: (Card) -> Boolean = { false },
)

@Composable
internal fun TvTitleMenu(card: Card, scale: Float, actions: TvTitleMenuActions, expanded: Boolean, onDismiss: () -> Unit) {
    if (!expanded) return
    Popup(onDismissRequest = onDismiss, popupPositionProvider = remember { TvTitleMenuPosition() }, properties = PopupProperties(focusable = true)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.requestFocus() }
        Column(Modifier.width(360.dp * scale)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) { onDismiss(); true }
                else false
            }
            .graphicsLayer { shape = RoundedCornerShape(14.dp * scale); shadowElevation = 16.dp.toPx() }
            .clip(RoundedCornerShape(14.dp * scale))
            .background(Palette.surface)
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp * scale))
            .padding(6.dp * scale)) {
            TvTitleMenuItem(if (card.progress != null) "Resume" else "Play", Lucide.Play, scale, first) { onDismiss(); actions.play(card) }
            TvTitleMenuItem("More info", Lucide.Info, scale) { onDismiss(); actions.info(card) }
            if (actions.save != null) {
                Box(Modifier.padding(horizontal = 14.dp * scale).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
                val saved = actions.saved(card)
                TvTitleMenuItem(if (saved) "In My List" else "Add to My List", if (saved) Lucide.Check else Lucide.Plus, scale) { onDismiss(); actions.save(card) }
            }
        }
    }
}

@Composable
private fun TvTitleMenuItem(label: String, icon: ImageVector, scale: Float, requester: FocusRequester? = null, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp * scale)
    Box(Modifier.padding(horizontal = 6.dp * scale).fillMaxWidth().height(64.dp * scale)
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .clip(shape)
        .background(if (focused) Palette.text else Color.Transparent)
        .pointerClickable(interaction, indication = null, onClickLabel = label, role = Role.Button, shape = shape, hover = null, onClick = action)) {
        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp * scale),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp * scale)) {
            Icon(icon, null, Modifier.size(24.dp * scale), tint = if (focused) Palette.background else Palette.text)
            Text(label, color = if (focused) Palette.background else Palette.text, fontSize = (24 * scale).sp, lineHeight = (29 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        FocusOutline(focused, 10.dp * scale, 2.dp * scale, Modifier.matchParentSize())
    }
}

private class TvTitleMenuPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val margin = 24
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val below = anchorBounds.bottom + margin
        val above = anchorBounds.top - margin - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height - margin -> below
            above >= margin -> above
            else -> margin
        }
        return IntOffset(x, y)
    }
}