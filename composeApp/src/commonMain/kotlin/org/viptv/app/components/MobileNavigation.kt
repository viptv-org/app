package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

internal val BottomBarHeight = 80.dp

@Composable
fun BottomNavigation(selected: Screen, navigate: (Screen) -> Unit) {
    val destinations = desktopDestinations.filter { it.screen in listOf(Screen.Home, Screen.Live, Screen.Calendar, Screen.Library) }
    Row(Modifier.fillMaxWidth().height(BottomBarHeight).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF292827).copy(alpha = .94f)).border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(32.dp))
            .padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { destination ->
                val pillFill = animateColorAsState(
                    if (selected == destination.screen) Color.White.copy(alpha = .09f) else Color.Transparent,
                    responseSpec(), label = "Bottom nav pill fill").value
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                    .background(pillFill)
                    .pointerClickable(shape = RoundedCornerShape(28.dp)) { navigate(destination.screen) }, horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(if (destination.screen == Screen.Live) Lucide.Tv else destination.icon,
                        destination.label, Modifier.size(22.dp), tint = if (selected == destination.screen) Palette.text else Palette.secondary)
                    Text(destination.label, color = Palette.text, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF343333))
            .border(1.dp, Color.White.copy(alpha = .1f), CircleShape).pointerClickable(shape = CircleShape) { navigate(Screen.Search) },
            contentAlignment = Alignment.Center) {
            Icon(Lucide.Search, "Search", Modifier.size(25.dp), tint = Palette.text)
        }
    }
}
