package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
            // `clip` is dropped on purpose: the focus ring is painted outside the row's bounds and a clip layer would erase it.
            val interactions = remember { MutableInteractionSource() }
            val hovered by interactions.collectIsHoveredAsState()
            var focused by remember { mutableStateOf(false) }
            Column(Modifier.width(56.dp).height(48.dp)
                .onFocusChanged { focused = it.isFocused }
                .tvFocusOutline(focused, 10.dp, darkGap = 2.dp, width = 2.dp)
                .background(railFill(destination.screen == selected, hovered), RoundedCornerShape(10.dp))
                .pointerClickable(interactionSource = interactions, indication = null, onClickLabel = destination.label, shape = RoundedCornerShape(10.dp), hover = null) { navigate(destination.screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
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
        RailAction(Lucide.Puzzle, "Add-ons") { navigate(Screen.Settings) }
        RailAction(Lucide.Settings, "Settings") { navigate(Screen.Settings) }
        Spacer(Modifier.height(8.dp))
    }
}

private fun railFill(selected: Boolean, hovered: Boolean): Color = when {
    selected -> if (hovered) HoverFill.surface4 else Color(0xFF2A2A2E)
    hovered -> HoverFill.row
    else -> Color.Transparent
}

@Composable
private fun RailAction(icon: ImageVector, label: String, action: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.size(48.dp)
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 10.dp, darkGap = 2.dp, width = 2.dp)
        .background(railFill(selected = false, hovered), RoundedCornerShape(10.dp))
        .pointerClickable(interactionSource = interactions, indication = null, onClickLabel = label, onClick = action, shape = RoundedCornerShape(10.dp), hover = null),
        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp), tint = Palette.secondary) }
}

object DesktopBreakpoints {
    val miniMax = 560.dp
    val narrowMax = 720.dp
    val heroStackMax = 760.dp
    val heroWideMin = 1800.dp
    const val heroArtFraction = 0.53f
    val heroArtMax = 774.dp
    val heroSummaryMax = 640.dp
    val heroSummaryWideMax = 800.dp
}
