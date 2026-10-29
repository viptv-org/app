package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import org.viptv.app.theme.Palette

@Composable
internal fun DeviceCard(compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (compact) {
        // The Column wraps its content height: a size-filling Column clips its trailing children in the wasm canvas renderer.
        Box(Modifier.fillMaxSize().background(Palette.background), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.padding(horizontal = 24.dp).padding(top = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    } else {
        Box(Modifier.fillMaxSize().background(Palette.background), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).background(Palette.surface, RoundedCornerShape(18.dp)).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun DeviceTile(size: Int = 64, icon: Int = 32) {
    Box(
        Modifier.size(size.dp).background(Color(0x14FFFFFF), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        TvGlyph(Modifier.size(icon.dp), Palette.body, 1.8f)
    }
}

@Composable
internal fun TvGlyph(modifier: Modifier, tint: Color, stroke: Float = 1.8f) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val strokeWidth = w / 24f * stroke
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
        val antenna = Path().apply {
            moveTo(w * 0.71f, h * 0.08f)
            lineTo(w * 0.50f, h * 0.30f)
            lineTo(w * 0.29f, h * 0.08f)
        }
        drawPath(antenna, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
internal fun DeviceTitle(text: String, compact: Boolean = false) {
    DeviceText(text, if (compact) 26.sp else 30.sp, Palette.text, FontWeight.Bold)
}

@Composable
internal fun DeviceSubtitle(text: String, compact: Boolean = false) {
    DeviceText(text, 15.sp, Palette.secondary, FontWeight.Normal)
}

@Composable
internal fun DeviceFootnote(text: String) {
    DeviceText(text, 13.sp, Palette.tertiary, FontWeight.Normal)
}

@Composable
private fun DeviceText(text: String, size: TextUnit, color: Color, weight: FontWeight) {
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        lineHeight = size * 1.15,
        textAlign = TextAlign.Center,
    )
}
