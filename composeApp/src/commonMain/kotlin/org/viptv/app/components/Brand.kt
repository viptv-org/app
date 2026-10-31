package org.viptv.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.resources.viptv_v
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontFamily

private const val InkHeight = 0.4125f
private const val InkAspect = 195.506f / 167f

@Composable
fun Brand(iconSize: androidx.compose.ui.unit.Dp = 23.dp, textSize: androidx.compose.ui.unit.TextUnit = 17.sp, gap: androidx.compose.ui.unit.Dp = 7.dp,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        BrandMark(iconSize)
        Text("VIPTV", fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold, fontSize = textSize,
            letterSpacing = letterSpacing, lineHeight = lineHeight)
    }
}

@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(Palette.accent, RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Image(painterResource(Res.drawable.viptv_v), null, Modifier.height(size * InkHeight).width(size * InkHeight * InkAspect),
            colorFilter = ColorFilter.tint(Palette.background))
    }
}
