package org.viptv.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.*

@Composable
internal fun DesktopWindowButtons(
    mac: Boolean,
    minimize: () -> Unit,
    maximize: () -> Unit,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 48.dp,
    maximized: Boolean = false,
) {
    if (mac) {
        Row(modifier.height(barHeight).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrafficLight(Color(0xFFFF5F57), "Close", close)
            TrafficLight(Color(0xFFFEBC2E), "Minimise", minimize)
            TrafficLight(Color(0xFF28C840), "Maximise", maximize)
        }
    } else {
        Row(modifier.height(barHeight), verticalAlignment = Alignment.CenterVertically) {
            WindowButton(Res.drawable.ic_window_minimize_zip, "Minimise", minimize)
            // A maximised window's third button must offer "restore", not a second
            // maximize (DUI-05) — a11y label included.
            WindowButton(if (maximized) null else Res.drawable.ic_window_maximize_zip,
                if (maximized) "Restore" else "Maximise", maximize)
            WindowButton(Res.drawable.ic_window_close_zip, "Close", close)
        }
    }
}

@Composable
private fun TrafficLight(color: Color, label: String, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.size(12.dp)
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 6.dp, darkGap = 2.dp, width = 2.dp)
        .background(if (hovered) color.copy(alpha = .85f) else color, CircleShape)
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = action))
}

@Composable
private fun WindowButton(icon: DrawableResource?, label: String, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val background = if (!hovered) Color.Transparent else if (label == "Close") Color(0xFFE81123) else Color.White.copy(alpha = .08f)
    Box(Modifier.width(46.dp).fillMaxHeight()
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 4.dp, darkGap = 2.dp, width = 2.dp)
        .background(background).hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = action),
        contentAlignment = Alignment.Center) {
        if (icon != null) Icon(painterResource(icon), label, Modifier.size(10.dp), tint = Palette.text)
        else RestoreGlyph(Modifier.size(10.dp))
    }
}

/** Restore: a front plate outline plus the back plate's top-right corner. */
@Composable
private fun RestoreGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        val edge = size.height * .72f
        val backLeft = size.width - edge
        drawLine(Palette.text, Offset(backLeft, stroke / 2), Offset(size.width, stroke / 2), stroke)
        drawLine(Palette.text, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, edge), stroke)
        drawRect(Palette.text, topLeft = Offset(stroke / 2, size.height - edge),
            size = Size(edge - stroke, edge - stroke), style = Stroke(width = stroke))
    }
}
