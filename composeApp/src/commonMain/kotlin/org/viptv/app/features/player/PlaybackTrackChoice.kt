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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette

@Composable
internal fun PlaybackTrackRow(primary: String, secondary: String?, selected: Boolean, modifier: Modifier = Modifier,
    pending: Boolean = false, error: String? = null, retry: (() -> Unit)? = null, choose: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val highlighted = selected && !pending
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 8.dp, width = 2.dp)
        .background(if (highlighted) Color.White.copy(alpha = .1f) else Color.Transparent, RoundedCornerShape(8.dp))
        .pointerSelectable(highlighted, role = Role.RadioButton, onClick = choose, shape = RoundedCornerShape(8.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.CenterStart) {
            when {
                pending -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Palette.text)
                selected -> Icon(Lucide.Check, null, Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(primary, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (error != null) {
                Text(error, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Palette.danger)
            } else if (secondary != null) {
                Text(secondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = Palette.secondary)
            }
        }
        if (error != null && retry != null) {
            Spacer(Modifier.width(12.dp))
            Text("Retry", Modifier.pointerClickable(role = Role.Button, onClick = retry, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold, color = Palette.accent)
        }
    }
}
