package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/** Tv3SetDisplay's four accent swatches, in board order. */
private data class TvAccentChoice(val name: String, val color: Long)

private val TvAccentChoices = listOf(
    TvAccentChoice("Amber", 0xFFF5C542), TvAccentChoice("Coral", 0xFFFF8B5C),
    TvAccentChoice("Mint", 0xFF62D9BC), TvAccentChoice("Periwinkle", 0xFFA3BCFF),
)

/**
 * Owns the Display record. The caller keeps it for as long as the section is
 * open; every change is written straight through, so leaving the page or a
 * power cut cannot lose a choice.
 */
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

/** The Display setting's Reduce motion, resolved against the device's own setting. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalPlatformContext.current
    return remember(context) {
        val display = decodeTvDisplay(runCatching { deviceRecordStore(context, DeviceRecord.Display).read() }.getOrNull())
        reducedMotion(display, systemReducedMotion(context))
    }
}

@Composable
internal fun TvDisplaySettings(prefs: TvDisplayPreferences, scale: Float, initial: FocusRequester, sidebar: FocusRequester,
    choose: (String, List<String>, (String) -> Unit) -> Unit, change: (TvDisplayPreferences) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        TvSettingsHeading("Display", scale)
        // Board order; the accent row carries the page's landing focus.
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
        // The board draws no control on this row, so none is added; the row
        // itself flips the persisted flag.
        TvSettingsRow("Fit to screen", "Use if the edges of the picture are cut off", scale, left = sidebar) {
            change(prefs.copy(fitToScreen = !prefs.fitToScreen))
        }
    }
}

/**
 * The accent row needs swatches where TvSettingsRow draws its value/switch, so
 * it repeats that row's focus treatment verbatim (ring outside the bounds,
 * light fill, shadow, TvFocusColumn clamp) instead of growing a second focus
 * style. Fold it into TvSettingsRow's trailing slot when that file is editable.
 *
 * OK cycles the swatches: per-swatch focus nodes would break the column clamp
 * and add four stops to the D-pad path.
 */
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
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            change(prefs.copy(accent = TvAccentChoices[(TvAccentChoices.indexOf(selected) + 1) % TvAccentChoices.size].color)) }
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text("Accent colour", color = color, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
            Text("Progress bars, marks and toggles", color = if (focused) Color(0xFF4A4945) else Palette.secondary,
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp, fontWeight = FontWeight.Medium)
        }
        // Paint only: the row stays the single focus target on this line.
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
