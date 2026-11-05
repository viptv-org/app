package org.viptv.app.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun TabletNavigation(selected: Screen, profile: Profile?, navigate: (Screen) -> Unit) {
    BoxWithConstraints(Modifier.width(92.dp).fillMaxHeight().tvBackdropBlur(24.dp)
        .background(Color(0xFF0B0B0C).copy(alpha = .55f))
        .drawBehind { drawRect(Color.White.copy(alpha = .08f), Offset(size.width - 1.dp.toPx(), 0f), Size(1.dp.toPx(), size.height)) }) {
        val short = maxHeight < TabletMinSide
        Column(Modifier.fillMaxSize().padding(top = if (short) 8.dp else 40.dp, bottom = if (short) 8.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!short) Box(Modifier.padding(bottom = 12.dp)) { BrandMark(40.dp) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (short) 4.dp else 24.dp)) {
                phoneDestinations.forEach { entry ->
                    val railFill = animateColorAsState(
                        if (selected == entry.screen) Color.White.copy(alpha = .16f) else Color.Transparent,
                        responseSpec(), label = "Tablet rail fill").value
                    Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).pointerClickable { navigate(entry.screen) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(56.dp, 32.dp).background(railFill, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(entry.icon, if (short) entry.label else null, Modifier.size(22.dp),
                                tint = if (selected == entry.screen) Palette.text else Palette.secondary)
                        }
                        if (!short) Text(entry.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected == entry.screen) Palette.text else Palette.secondary)
                    }
                }
            }
            if (short) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RailSettings(navigate)
                RailProfile(profile, navigate, 44.dp, 32.dp, 8.dp)
            } else Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RailSettings(navigate)
                RailProfile(profile, navigate, 36.dp, 36.dp, 9.dp)
            }
        }
    }
}

@Composable
private fun RailSettings(navigate: (Screen) -> Unit) {
    IconButton(onClick = { navigate(Screen.Settings) }, modifier = Modifier.size(44.dp)) {
        Icon(Lucide.Settings, "Settings", Modifier.size(22.dp), tint = Palette.text)
    }
}

@Composable
private fun RailProfile(profile: Profile?, navigate: (Screen) -> Unit, target: Dp, avatar: Dp, radius: Dp) {
    Box(Modifier.size(target).semantics { contentDescription = "Switch profile" }.pointerClickable { navigate(Screen.Profiles) },
        contentAlignment = Alignment.Center) { ProfileAvatar(profile, avatar, radius) }
}
