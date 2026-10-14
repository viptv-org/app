package org.viptv.app.features.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun PairingBackLink(back: () -> Unit, mobile: Boolean) {
    Row(Modifier.height(20.dp).clickable(onClick = back), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Lucide.ChevronLeft, null, Modifier.size(14.dp), tint = Palette.secondary)
        Text("All sign-in options", color = Palette.secondary, fontSize = if (mobile) 14.sp else 13.sp,
            fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
    }
}
