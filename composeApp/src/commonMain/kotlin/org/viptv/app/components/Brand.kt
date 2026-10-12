package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily

@Composable
fun Brand(iconSize: androidx.compose.ui.unit.Dp = 23.dp, textSize: androidx.compose.ui.unit.TextUnit = 17.sp, gap: androidx.compose.ui.unit.Dp = 7.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        BrandMark(iconSize)
        Text("VIPTV", fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold, fontSize = textSize)
    }
}

@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(Palette.accent, RoundedCornerShape((size.value * 0.28f).dp)), contentAlignment = Alignment.Center) {
        Text("V", color = Palette.background, fontWeight = FontWeight.Black, fontSize = (size.value * 0.65f).sp)
    }
}
