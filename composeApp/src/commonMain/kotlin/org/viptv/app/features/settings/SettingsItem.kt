package org.viptv.app.features.settings

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
import org.viptv.app.components.pointerClickable

@Composable
internal fun SettingsItem(label: String, icon: ImageVector, subtitle: String? = null, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().pointerClickable(onClick = action).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Palette.secondary) }
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = Palette.tertiary)
    }
}

@Composable
internal fun SettingsDangerItem(label: String, icon: ImageVector, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().pointerClickable(onClick = action).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = Palette.danger)
        Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.danger)
    }
}
