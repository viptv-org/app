package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackTrackChoice(label: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (selected) Palette.raised else Palette.surface, RoundedCornerShape(8.dp))
        .clickable(onClick = choose).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp)) { if (selected) Icon(Lucide.Check, null, Modifier.size(16.dp)) }
        Text(label, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}
