package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_remote_zip

@Composable
fun SideNavigation(selected: Screen, navigate: (Screen) -> Unit) {
    Column(Modifier.width(72.dp).fillMaxHeight().background(Palette.background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        desktopDestinations.forEach { destination ->
            if (destination.screen == Screen.Library) {
                HorizontalDivider(Modifier.width(32.dp).padding(vertical = 8.dp), color = Color.White.copy(alpha = .08f))
            }
            Column(Modifier.width(56.dp).height(48.dp).clip(RoundedCornerShape(10.dp))
                .background(if (destination.screen == selected) Color(0xFF2A2A2D) else Color.Transparent)
                .clickable { navigate(destination.screen) }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                val tint = if (destination.screen == selected) Palette.text else Palette.secondary
                if (destination.screen == Screen.Live) Icon(painterResource(Res.drawable.ic_remote_zip), destination.label, Modifier.size(20.dp), tint = tint)
                else Icon(destination.icon, destination.label, Modifier.size(20.dp), tint = tint)
                Spacer(Modifier.height(3.dp))
                Text(destination.label, fontSize = 10.sp, lineHeight = 12.sp, color = Palette.text)
            }
            if (destination.screen != Screen.Calendar) Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(Lucide.Puzzle, "Add-ons", Modifier.size(20.dp)) }
        IconButton(onClick = { navigate(Screen.Settings) }) { Icon(Lucide.Settings, "Settings", Modifier.size(20.dp)) }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * DUI-04: the desktop window sizes are a single decision, not copies of a magic
 * number. The rail, the title bar and the hero all read these values so a resize
 * can never leave the chrome and the content on different breakpoints.
 *
 * It lives beside the rail because that is the component every tier is measured
 * against.
 */
object DesktopBreakpoints {
    /** Narrowest tier — the width of the D3Mini window. */
    val miniMax = 560.dp
    /** Rail/menu-button switch: D3HomeNarrow (600, no rail) vs D3HomeCompact (1024, 72dp rail). */
    val narrowMax = 720.dp
    /** Below this hero width the copy stacks under the artwork (D3HomeNarrow). */
    val heroStackMax = 760.dp
    /** D3HomeUltra and wider get the 800.dp copy column instead of 640.dp. */
    val heroWideMin = 1800.dp
    /** Hero artwork takes 53% of the content width, capped at the D3HomeUltra 774px. */
    const val heroArtFraction = 0.53f
    val heroArtMax = 774.dp
    val heroSummaryMax = 640.dp
    val heroSummaryWideMax = 800.dp
}
