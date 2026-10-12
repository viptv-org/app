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
fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(23.dp).background(Palette.accent, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("V", color = Palette.background, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Text("VIPTV", fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}
