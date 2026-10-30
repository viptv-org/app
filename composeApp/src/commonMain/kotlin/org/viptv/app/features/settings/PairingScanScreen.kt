package org.viptv.app.features.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import org.viptv.app.components.pointerClickable
import org.viptv.app.theme.Palette

@Composable
internal fun PairingScanScreen(preview: @Composable (Modifier) -> Unit, allowed: Boolean, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101012))) {
        if (allowed) preview(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scan the code on your TV", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                    Text(if (allowed) "Point your camera at the code the TV shows." else "Enter the code on your TV instead.",
                        color = Palette.secondary, fontSize = 14.sp, lineHeight = 19.sp)
                }
                Box(Modifier.size(44.dp).background(Color(0x33FFFFFF), RoundedCornerShape(22.dp)).pointerClickable(onClick = dismiss),
                    contentAlignment = Alignment.Center) { Icon(Lucide.X, "Close", Modifier.size(20.dp), Palette.text) }
            }
            Spacer(Modifier.weight(1f))
            Viewfinder(Modifier.size(260.dp))
            Spacer(Modifier.weight(1f))
            if (!allowed) {
                Text("VIPTV needs the camera to read the code. Allow it in Settings, or type the code instead.",
                    color = Palette.tertiary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.fillMaxWidth().height(50.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(12.dp)).pointerClickable(onClick = dismiss),
                contentAlignment = Alignment.Center) {
                Text("Enter the code instead", color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val arm = side * 0.16f
        val stroke = side * 0.014f
        listOf(
            Offset(0f, 0f) to Offset(1f, 1f),
            Offset(side, 0f) to Offset(-1f, 1f),
            Offset(0f, side) to Offset(1f, -1f),
            Offset(side, side) to Offset(-1f, -1f),
        ).forEach { (corner, direction) ->
            drawLine(Palette.text, corner, corner + Offset(direction.x * arm, 0f), stroke, StrokeCap.Round)
            drawLine(Palette.text, corner, corner + Offset(0f, direction.y * arm), stroke, StrokeCap.Round)
        }
    }
}
