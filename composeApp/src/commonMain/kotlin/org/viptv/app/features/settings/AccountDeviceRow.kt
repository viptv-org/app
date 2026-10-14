package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.AccountDevice
import org.viptv.app.connection.historyDayLabel
import org.viptv.app.theme.Palette

internal fun AccountDevice.displayName(): String = if (current) "This browser"
    else name.trim().ifEmpty { if (kind == "browser") "Browser" else "VIPTV app" }

@Composable
internal fun AccountDeviceRow(device: AccountDevice, busy: Boolean, compact: Boolean, signOut: () -> Unit) {
    val joined = historyDayLabel(device.created_at * 1000L).let { if (it == "Today" || it == "Yesterday") it.lowercase() else it }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(Palette.text.copy(alpha = .08f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(if (device.kind == "browser") Lucide.ExternalLink else Lucide.Monitor, null, Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.displayName(), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (device.current) "Now" else "Signed in $joined",
                fontSize = 13.sp, lineHeight = 18.sp, color = Palette.secondary)
        }
        if (device.current) Text("This device", color = Palette.tertiary, fontSize = 13.sp)
        else if (device.can_sign_out) AccountSignOutButton("Sign out", busy, compact, signOut)
    }
}
