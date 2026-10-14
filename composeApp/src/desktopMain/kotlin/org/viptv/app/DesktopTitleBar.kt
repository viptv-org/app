package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette

@Composable
internal fun DesktopTitleBar(
    state: ApplicationState,
    mac: Boolean,
    navigate: (Screen) -> Unit,
    search: (String) -> Unit,
    menu: () -> Unit,
    minimize: () -> Unit,
    maximize: () -> Unit,
    close: () -> Unit,
    dragModifier: Modifier,
    back: (() -> Unit)? = null,
    forward: (() -> Unit)? = null,
    cast: (() -> Unit)? = null,
    remote: (() -> Unit)? = null,
) {
    val simple = state.screen in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit)
    val barHeight = if (simple) 40.dp else 48.dp
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.searchTerm) { query = state.searchTerm }
    BoxWithConstraints(Modifier.fillMaxWidth().height(barHeight).background(Palette.background).then(dragModifier)) {
        val narrow = maxWidth < 720.dp
        Row(Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(start = if (simple) 14.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mac) {
                DesktopWindowButtons(true, minimize, maximize, close, barHeight = barHeight)
                if (simple) Spacer(Modifier.width(8.dp))
            }
            if (simple) {
                DesktopBrand()
            } else {
                if (narrow) ChromeButton(ChromeIcon.Menu, "Menu", Palette.text, menu)
                ChromeButton(ChromeIcon.Back, "Back", Palette.secondary, onClick = back)
                ChromeButton(ChromeIcon.Forward, "Forward", Palette.secondary, onClick = forward)
                if (!mac && !narrow) { Spacer(Modifier.width(4.dp)); DesktopBrand() }
            }
        }
        if (!simple) {
            if (narrow) {
                ChromeButton(ChromeIcon.Search, "Search", Palette.text, { navigate(Screen.Search) },
                    Modifier.align(Alignment.Center))
            } else {
                val searchWidth = minOf(460.dp, maxWidth * .36f)
                Row(Modifier.align(Alignment.Center).width(searchWidth).height(32.dp)
                    .background(Color(0xFF1E1E21), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DesktopChromeIcon(ChromeIcon.Search, Palette.text, Modifier.size(16.dp))
                    BasicTextField(query, { query = it; navigate(Screen.Search); search(it) },
                        Modifier.weight(1f), singleLine = true, cursorBrush = SolidColor(Palette.text),
                        textStyle = TextStyle(color = Palette.text, fontSize = 13.sp,
                            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Search movies, series and channels", color = Palette.secondary, fontSize = 13.sp)
                                inner()
                            }
                        })
                    Text(if (mac) "⌘K" else "Ctrl K", color = Palette.secondary, fontSize = 11.sp,
                        modifier = Modifier.background(Color(0xFF121214), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            if (!simple) {
                ChromeButton(ChromeIcon.Cast, "Watch on TV", Palette.text, cast)
                ChromeButton(ChromeIcon.Remote, "TV remote", Palette.text, remote)
                state.profiles.find { it.id == state.selectedProfile }?.let { profile ->
                    Box(Modifier.padding(start = 4.dp, end = 8.dp).clickable { navigate(Screen.Profiles) }) {
                        ProfileAvatar(profile, 28.dp, 7.dp)
                    }
                }
            }
            if (!mac) DesktopWindowButtons(false, minimize, maximize, close, barHeight = barHeight)
            else Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(Color(0xFF1E1E21)))
    }
}

@Composable
private fun DesktopBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(16.dp).background(Palette.accent, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text("V", color = Palette.background, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text("VIPTV", color = Palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)))
    }
}

@Composable
private fun ChromeButton(icon: ChromeIcon, label: String, color: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).semantics { contentDescription = label }
        .clickable(enabled = onClick != null, onClickLabel = label) { onClick?.invoke() }, contentAlignment = Alignment.Center) {
        DesktopChromeIcon(icon, if (onClick == null) color.copy(alpha = color.alpha * .38f) else color, Modifier.size(17.dp))
    }
}
