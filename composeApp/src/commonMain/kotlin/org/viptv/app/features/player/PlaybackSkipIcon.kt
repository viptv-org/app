package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*

@Composable
internal fun PlaybackSkipIcon(forward: Boolean, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(if (forward) Lucide.RotateCw else Lucide.RotateCcw,
            if (forward) "Forward 10 seconds" else "Back 10 seconds", Modifier.fillMaxSize())
        Text("10", fontSize = (size.value * 7.6f / 24f).sp, lineHeight = (size.value * 9f / 24f).sp, fontWeight = FontWeight.Bold)
    }
}
