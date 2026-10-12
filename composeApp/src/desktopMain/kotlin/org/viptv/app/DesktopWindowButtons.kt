package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun DesktopWindowButtons(
    mac: Boolean,
    minimize: () -> Unit,
    maximize: () -> Unit,
    close: () -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 48.dp,
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
            WindowButton("−", "Minimise", minimize)
            WindowButton("□", "Maximise", maximize)
            WindowButton("×", "Close", close)
        }
    }
}

@Composable
private fun TrafficLight(color: Color, label: String, action: () -> Unit) {
    Box(Modifier.size(12.dp).background(color, CircleShape).clickable(onClickLabel = label, onClick = action))
}

@Composable
private fun WindowButton(glyph: String, label: String, action: () -> Unit) {
    Box(Modifier.width(46.dp).fillMaxHeight().clickable(onClickLabel = label, onClick = action), contentAlignment = Alignment.Center) {
        Text(glyph, color = Palette.text, fontSize = if (glyph == "×") 19.sp else 16.sp,
            textAlign = TextAlign.Center)
    }
}
