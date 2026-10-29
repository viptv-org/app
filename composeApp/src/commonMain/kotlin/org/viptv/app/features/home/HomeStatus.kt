package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.app.components.Button

@Composable
internal fun HomeStatus(home: HomeSnapshot?, error: String?, busy: Boolean, retry: () -> Unit,
    navigate: (Screen) -> Unit, modifier: Modifier = Modifier, television: Boolean = false, scale: Float = 1f, inline: Boolean = false) {
    val unconfigured = error == null && home?.complete == true && home.rows.isEmpty()
    val hasContent = home?.hero != null || home?.featured?.isNotEmpty() == true || home?.rows?.any { it.cards.isNotEmpty() } == true
    val title = when {
        error != null -> if (!hasContent) "Could not load Home" else "Could not complete this action"
        unconfigured -> "No catalogs configured"
        home == null -> "Home isn’t available yet"
        else -> "Nothing to show yet"
    }
    val message = error ?: if (unconfigured) "Manage your integrations in Settings to add catalogs for this profile."
        else "Try refreshing Home to load available titles."
    val label = if (unconfigured) "Open settings" else if (busy) "Refreshing Home…" else "Retry Home"
    val action = if (unconfigured) ({ navigate(Screen.Settings) }) else retry
    val focus = remember { FocusRequester() }
    if (television) TvRequestedFocus(focus, title, !busy)
    Surface(modifier.widthIn(max = if (inline) 1728.dp * scale else if (television) 1000.dp * scale else 680.dp), color = Palette.surface,
        shape = RoundedCornerShape(12.dp * scale)) {
        if (inline) Row(Modifier.padding(20.dp * scale), horizontalArrangement = Arrangement.spacedBy(24.dp * scale),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, color = Palette.secondary,
                fontSize = (24 * scale).sp, lineHeight = (34 * scale).sp)
            TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale,
                requester = focus, enabled = !busy, activate = action) {
                Text(label, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, color = it)
            }
        } else Column(Modifier.padding((if (television) 28 else 20).dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = ((if (television) 32 else 22) * scale).sp, color = Palette.text)
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = Palette.secondary,
                fontSize = ((if (television) 24 else 16) * scale).sp, lineHeight = ((if (television) 34 else 24) * scale).sp)
            if (television) TvAction(Modifier.width(300.dp * scale).height(72.dp * scale), scale,
                requester = focus, enabled = !busy, activate = action) {
                Text(label, fontSize = (24 * scale).sp, fontWeight = FontWeight.SemiBold, color = it)
            } else Button(action, enabled = !busy) { Text(label) }
        }
    }
}
