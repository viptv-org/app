package org.viptv.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.resources.Res
import org.viptv.app.resources.bricolage
import org.viptv.app.state.ApplicationState
import org.viptv.app.components.DesktopBreakpoints
import org.viptv.app.components.BrandMark
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerHovered
import org.viptv.app.components.responseSpec

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
    searchFocus: FocusRequester? = null,
    maximized: Boolean = false,
) {
    val simple = state.screen in listOf(Screen.Connect, Screen.Profiles, Screen.ProfilePin, Screen.ProfileEdit)
    val barHeight = if (simple) 40.dp else 48.dp
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.searchTerm) { query = state.searchTerm }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(barHeight).background(Palette.background).then(dragModifier)) {
        val narrow = maxWidth < DesktopBreakpoints.narrowMax
        val mini = maxWidth < DesktopBreakpoints.miniMax
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
                val searchHover = remember { MutableInteractionSource() }
                val searchHovered by searchHover.collectIsHoveredAsState()
                val searchFill by animateColorAsState(if (searchHovered) SearchFieldHover else SearchField, responseSpec(), label = "Search field fill")
                Row(Modifier.align(Alignment.Center).width(searchWidth).height(32.dp)
                    .hoverable(searchHover)
                    .onFocusChanged { searchFocused = it.hasFocus }
                    .tvFocusOutline(searchFocused, 8.dp, darkGap = 2.dp, width = 2.dp)
                    .background(searchFill, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DesktopChromeIcon(ChromeIcon.Search, Palette.text, Modifier.size(16.dp))
                    BasicTextField(query, { query = it; if (it.isNotEmpty()) navigate(Screen.Search); search(it) },
                        Modifier.weight(1f).then(if (searchFocus != null) Modifier.focusRequester(searchFocus) else Modifier)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                                    Key.Escape -> {
                                        // Esc dismisses search: clear the query, drop focus and leave the results page so the content gets focus back.
                                        query = ""; search(""); focusManager.clearFocus()
                                        // Return to the page search was opened from, not to the details route's return target.
                                        if (state.screen == Screen.Search) (back ?: { navigate(Screen.Home) })()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        singleLine = true, cursorBrush = SolidColor(Palette.text),
                        textStyle = TextStyle(color = Palette.text, fontSize = 13.sp,
                            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Search movies, series and channels", color = Palette.secondary, fontSize = 13.sp)
                                inner()
                            }
                        })
                    if (query.isNotEmpty()) TitleBarHit("Clear search", { query = ""; search(""); focusManager.clearFocus() },
                        Modifier.size(20.dp), radius = 10.dp, hover = Color(0xFF34343A)) {
                        Icon(Lucide.X, "Clear search", Modifier.size(14.dp), tint = Palette.secondary)
                    }
                    Text(if (mac) "⌘K" else "Ctrl K", color = Palette.secondary, fontSize = 11.sp,
                        modifier = Modifier.background(Color(0xFF121214), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            if (!simple) {
                if (!mini) ChromeButton(ChromeIcon.Cast, "Watch on TV", Palette.text, cast)
                if (!mini) ChromeButton(ChromeIcon.Remote, "TV remote", Palette.text, remote)
                state.profiles.find { it.id == state.selectedProfile }?.let { profile ->
                    TitleBarHit("Switch profile", { navigate(Screen.Profiles) },
                        Modifier.padding(start = 4.dp, end = 8.dp), radius = 7.dp) {
                        ProfileAvatar(profile, 28.dp, 7.dp)
                    }
                }
            }
            if (!mac) DesktopWindowButtons(false, minimize, maximize, close, barHeight = barHeight, maximized = maximized)
            else Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.BottomCenter).background(Color(0xFF1E1E21)))
    }
}

private val SearchField = Color(0xFF1E1E21)
private val SearchFieldHover = Color.White.copy(alpha = .08f).compositeOver(SearchField)

@Composable
private fun DesktopBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        BrandMark(16.dp)
        Text("VIPTV", color = Palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily(Font(Res.font.bricolage, FontWeight.ExtraBold)))
    }
}

@Composable
private fun ChromeButton(icon: ChromeIcon, label: String, color: Color, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused || interaction.pointerHovered()) Color.White.copy(alpha = if (focused) .16f else .08f) else Color.Transparent,
        responseSpec(), label = "Chrome button fill")
    Box(modifier.size(36.dp).semantics { contentDescription = label }
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 8.dp)
        .background(fill, RoundedCornerShape(8.dp))
        .pointerClickable(interactionSource = interaction, enabled = onClick != null, onClickLabel = label, shape = RoundedCornerShape(8.dp), hover = null) { onClick?.invoke() },
        contentAlignment = Alignment.Center) {
        DesktopChromeIcon(icon, if (onClick == null) color.copy(alpha = color.alpha * .38f) else color, Modifier.size(17.dp))
    }
}

@Composable
private fun TitleBarHit(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    radius: Dp = 8.dp, hover: Color = Color.White.copy(alpha = .08f), content: @Composable () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (interactions.pointerHovered()) hover else Color.Transparent, responseSpec(), label = "Title bar fill")
    Box(modifier
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, radius, darkGap = 2.dp, width = 2.dp)
        .background(fill, RoundedCornerShape(radius))
        .pointerClickable(interactionSource = interactions, indication = null, onClickLabel = label, onClick = onClick, shape = RoundedCornerShape(radius), hover = null),
        contentAlignment = Alignment.Center) { content() }
}
