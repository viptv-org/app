package org.viptv.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.*
import org.viptv.app.components.pointerClickable

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

// app#93: window controls are native chrome, not TV controls. Hover/press use the
// native palette (Close #E81123 → #F1707A, the others an 8 % → 12 % white wash),
// and they never draw the board's outside focus ring, which was clipped by the
// window edge and overlapped its neighbour.
private val WindowCloseHover = Color(0xFFE81123)
private val WindowClosePress = Color(0xFFF1707A)
private val WindowButtonHover = Color.White.copy(alpha = .08f)
private val WindowButtonPress = Color.White.copy(alpha = .12f)

@Composable
private fun TrafficLight(color: Color, label: String, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(Modifier.size(12.dp)
        .background(if (hovered) color.copy(alpha = .85f) else color, CircleShape)
        .hoverable(interaction)
        .pointerClickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = action, shape = CircleShape, hover = null))
}

@Composable
private fun WindowButton(icon: DrawableResource?, label: String, action: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val close = label == "Close"
    val background = when {
        pressed -> if (close) WindowClosePress else WindowButtonPress
        hovered -> if (close) WindowCloseHover else WindowButtonHover
        else -> Color.Transparent
    }
    Box(Modifier.width(46.dp).fillMaxHeight()
        .background(background).hoverable(interaction)
        .pointerClickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = action),
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
