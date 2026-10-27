package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun TabletNavigation(selected: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    Column(Modifier.width(92.dp).fillMaxHeight().tvBackdropBlur(24.dp)
        .background(Color(0xFF0B0B0C).copy(alpha = .55f))
        .drawBehind { drawRect(Color.White.copy(alpha = .08f), Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) }
        .padding(top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.padding(bottom = 12.dp)) { BrandMark(40.dp) }
        val entries = listOf(Destination(Screen.Home, "Home", Lucide.House), Destination(Screen.Live, "Live", Lucide.Tv),
            Destination(Screen.Library, "My List", Lucide.Bookmark), Destination(Screen.Search, "Search", Lucide.Search))
        entries.forEach { entry ->
            Column(Modifier.fillMaxWidth().pointerClickable { navigate(entry.screen) }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(56.dp, 32.dp).background(if (selected == entry.screen) Color.White.copy(alpha = .16f) else Color.Transparent,
                    RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(entry.icon, null, Modifier.size(22.dp), tint = if (selected == entry.screen) Palette.text else Palette.secondary)
                }
                Text(entry.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected == entry.screen) Palette.text else Palette.secondary)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconButton(onClick = { navigate(Screen.Settings) }, modifier = Modifier.size(44.dp)) { Icon(Lucide.Settings, "Settings", Modifier.size(22.dp), tint = Palette.text) }
            Box(Modifier.size(36.dp).pointerClickable { navigate(Screen.Profiles) }, contentAlignment = Alignment.Center) { ProfileAvatar(profile, 36.dp, 9.dp) }
        }
    }
}
