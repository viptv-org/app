package org.viptv.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.theme.Palette

@Composable
internal fun SettingsItem(label: String, icon: ImageVector, subtitle: String? = null, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Palette.secondary) }
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = Palette.secondary)
    }
}
