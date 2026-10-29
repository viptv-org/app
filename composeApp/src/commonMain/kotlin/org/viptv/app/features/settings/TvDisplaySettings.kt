package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import org.viptv.app.components.rememberTvColumnItem
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

private data class TvAccentChoice(val name: String, val color: Long)

private val TvAccentChoices = listOf(
    TvAccentChoice("Amber", 0xFFF5C542), TvAccentChoice("Coral", 0xFFFF8B5C),
    TvAccentChoice("Mint", 0xFF62D9BC), TvAccentChoice("Periwinkle", 0xFFA3BCFF),
)

@Stable
internal class TvDisplayState(initial: TvDisplayPreferences, private val store: DeviceRecordStore) {
    var value by mutableStateOf(initial)
        private set

    fun change(next: TvDisplayPreferences) {
        value = next
        runCatching { store.write(encodeTvDisplay(next)) }
    }
}

@Composable
internal fun rememberTvDisplayState(): TvDisplayState {
    val context = LocalPlatformContext.current
    val store = remember(context) { deviceRecordStore(context, DeviceRecord.Display) }
    return remember(store) { TvDisplayState(decodeTvDisplay(runCatching { store.read() }.getOrNull()), store) }
}

internal val LocalTvDisplayState = staticCompositionLocalOf<TvDisplayState?> { null }

@Composable
internal fun rememberSystemReducedMotion(): Boolean {
    val context = LocalPlatformContext.current
    return remember(context) { systemReducedMotion(context) }
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val display = LocalTvDisplayState.current ?: rememberTvDisplayState()
    return reducedMotion(display.value, rememberSystemReducedMotion())
}

@Composable
internal fun TvDisplaySettings(prefs: TvDisplayPreferences, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    choose: (String, List<String>, (String) -> Unit) -> Unit, change: (TvDisplayPreferences) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Display", scale)
        TvDisplayAccentRow(prefs, scale, initial, sidebar, change)
        TvSettingsRow("Show ratings on cards", scale = scale, checked = prefs.ratings, left = sidebar) {
            change(prefs.copy(ratings = !prefs.ratings))
        }
        TvSettingsRow("Text size", scale = scale, value = prefs.textSize, left = sidebar) {
            choose("Text size", listOf("Default", "Large", "Larger")) { change(prefs.copy(textSize = it)) }
        }
        TvSettingsRow("Reduce motion", scale = scale, value = prefs.reduceMotion, left = sidebar) {
            choose("Reduce motion", listOf("Follow TV", "On", "Off")) { change(prefs.copy(reduceMotion = it)) }
        }
        TvSettingsRow("Screen saver", scale = scale, value = prefs.screenSaver, left = sidebar) {
            choose("Screen saver", listOf("Off", "After 5 minutes", "After 10 minutes", "After 30 minutes")) {
                change(prefs.copy(screenSaver = it)) }
        }
        TvSettingsRow("Fit to screen", "Use if the edges of the picture are cut off", scale, left = sidebar) {
            change(prefs.copy(fitToScreen = !prefs.fitToScreen))
        }
    }
}

@Composable
private fun TvDisplayAccentRow(prefs: TvDisplayPreferences, scale: Float, requester: FocusRequester,
    left: FocusRequester, change: (TvDisplayPreferences) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) Palette.background else Palette.text
    val selected = TvAccentChoices.firstOrNull { it.color == prefs.accent } ?: TvAccentChoices.first()
    val (columnUp, columnDown) = rememberTvColumnItem(requester)
    Row(Modifier.fillMaxWidth().height(96.dp * scale).focusRequester(requester)
        .focusProperties {
            this.left = left
            columnUp?.let { this.up = it }
            columnDown?.let { this.down = it }
        }.onFocusChanged { focused = it.isFocused }
        .semantics { stateDescription = selected.name }
        .graphicsLayer {
            this.shape = RoundedCornerShape(14.dp * scale)
            shadowElevation = if (focused) 24.dp.toPx() * scale else 0f
            clip = false
        }
        .background(if (focused) Palette.text else Color(0xFF191919), RoundedCornerShape(14.dp * scale))
        .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, shape = RoundedCornerShape(14.dp * scale)) {
            change(prefs.copy(accent = TvAccentChoices[(TvAccentChoices.indexOf(selected) + 1) % TvAccentChoices.size].color)) }
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text("Accent colour", color = color, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
            Text("Progress bars, marks and toggles", color = if (focused) Color(0xFF4A4945) else Palette.secondary,
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            TvAccentChoices.forEach { choice ->
                val marked = choice.color == prefs.accent
                Box(Modifier.size(44.dp * scale)
                    .tvFocusOutline(marked, 22.dp * scale, scale, width = 3.dp, darkGap = 3.dp)
                    .background(Color(choice.color), CircleShape))
            }
        }
    }
}
