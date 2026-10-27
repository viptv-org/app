package org.viptv.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.components.pointerSelectable

@Composable
internal fun PlaybackTrackChoice(label: String, selected: Boolean, choose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // M3PlayerAudio: the current track is marked by a 0.1 white fill, a 16 dp
    // check and bold text (NAV-07). Focus only adds the ring and never changes
    // the row's shape or size (NAV-08).
    Row(Modifier.fillMaxWidth().height(40.dp).onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 8.dp, width = 2.dp)
        .background(if (selected) Color.White.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(8.dp))
        .pointerSelectable(selected = selected, role = Role.RadioButton, onClick = choose, shape = RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp)) { if (selected) Icon(Lucide.Check, null, Modifier.size(16.dp)) }
        Text(label, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
