package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

/**
 * TV Settings → Parental controls, to `Tv3SetParental`.
 *
 * The per-profile rating limit and the approved/blocked title list live in the
 * server's parental policy, which this TV screen is not given; the rows
 * therefore keep the board's geometry and copy and open the real editor
 * (Profiles → profile → Maturity rating / Profile lock) instead of pretending to
 * hold a value. No generic "not available" dialog is used.
 */
@Composable
internal fun TvParentalSettings(profile: Profile?, maturity: String?, pinAvailable: Boolean,
    scale: Float, first: FocusRequester, sidebar: FocusRequester, profiles: () -> Unit) {
    val pills = remember(profile) { listOfNotNull(profile) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        TvSettingsHeading("Parental controls", scale)
        Spacer(Modifier.height(24.dp * scale))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            pills.forEach { item ->
                var focused by remember { mutableStateOf(false) }
                Row(Modifier.height(72.dp * scale).focusRequester(if (item == pills.first()) first else remember { FocusRequester() })
                    .focusProperties { left = sidebar }
                    .onFocusChanged { focused = it.isFocused }
                    .tvFocusOutline(focused, 36.dp * scale, scale)
                    .background(if (focused) Palette.text else Color.White.copy(alpha = .12f), RoundedCornerShape(36.dp * scale))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = profiles)
                    .padding(horizontal = 22.dp * scale).padding(start = 10.dp * scale),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    org.viptv.app.features.entry.ProfileAvatar(item, 52.dp * scale, 12.dp * scale)
                    Text(item.name, color = if (focused) Palette.background else Palette.text,
                        fontSize = (24 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(42.dp * scale))
        RatingRow("Movies up to", listOf("G", "PG", "PG-13", "R", "NC-17"), maturity, pinAvailable, scale, sidebar, profiles)
        Spacer(Modifier.height(34.dp * scale))
        RatingRow("Series up to", listOf("TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA"), maturity, pinAvailable, scale, sidebar, profiles)
        Spacer(Modifier.height(30.dp * scale))
        Text("Titles above these ratings don’t show anywhere for ${profile?.name ?: "this profile"} — not in rows, search or Live.",
            color = Palette.tertiary, fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp)
        Spacer(Modifier.height(18.dp * scale))
        PolicyRow("Hide titles with no rating", null, null, scale, sidebar, profiles)
        Spacer(Modifier.height(12.dp * scale))
        PolicyRow("Blocked titles", "Hidden for ${profile?.name ?: "this profile"} even if the rating allows them", null, scale, sidebar, profiles)
        Spacer(Modifier.height(12.dp * scale))
        PolicyRow("PIN to leave ${profile?.name ?: "this profile"}’s profile",
            "Ask for a PIN before leaving this profile on this device",
            if (profile?.locked == true) "On" else "Off", scale, sidebar, profiles)
    }
}

@Composable
private fun RatingRow(label: String, ratings: List<String>, maturity: String?, pinAvailable: Boolean,
    scale: Float, sidebar: FocusRequester, profiles: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp * scale)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            Text(label, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
            maturity?.let {
                Text(it, color = Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp)
            }
            if (!pinAvailable) Text("Account policy", color = Palette.tertiary, fontSize = (20 * scale).sp)
        }
        // The chips are board-shaped; selecting one opens the real editor rather
        // than writing a value this screen does not own.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp * scale)) {
            ratings.forEach { rating ->
                val selected = maturity?.contains(rating, ignoreCase = true) == true
                var focused by remember { mutableStateOf(false) }
                Row(Modifier.height(64.dp * scale)
                    .onFocusChanged { focused = it.isFocused }
                    .tvFocusOutline(focused, 32.dp * scale, scale)
                    .background(if (focused || selected) Palette.text else Color.White.copy(alpha = .08f), RoundedCornerShape(32.dp * scale))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = profiles)
                    .padding(horizontal = 20.dp * scale), verticalAlignment = Alignment.CenterVertically) {
                    Text(rating, color = if (focused || selected) Palette.background else Palette.text,
                        fontSize = (22 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(title: String, subtitle: String?, value: String?, scale: Float, sidebar: FocusRequester, action: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(88.dp * scale)
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 14.dp * scale, scale)
        .background(if (focused) Palette.text else Color(0xFF191919), RoundedCornerShape(14.dp * scale))
        .focusProperties { left = sidebar }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
        .padding(horizontal = 28.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
            Text(title, color = if (focused) Palette.background else Palette.text, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = if (focused) Color(0xFF4A4945) else Palette.secondary, fontSize = (19 * scale).sp) }
        }
        value?.let {
            Text(it, color = if (focused) Color(0xFF4A4945) else Palette.secondary, fontSize = (22 * scale).sp)
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(24.dp * scale), tint = if (focused) Palette.background else Palette.secondary)
    }
}
