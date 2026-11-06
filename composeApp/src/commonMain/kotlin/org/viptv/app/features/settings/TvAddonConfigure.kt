package org.viptv.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.viptv.app.state.AddonConfigureRequest
import org.viptv.app.state.ADDON_CONFIGURE_FAILURE
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.IntegrationConnection

// OK on a TV add-on row toggles the switch, so its other actions live on a hold.
@Composable
internal fun TvAddonManageDialog(addon: IntegrationConnection, enabled: Boolean, configurable: Boolean, busy: Boolean,
    scale: Float, toggle: (Boolean) -> Unit, reconfigure: () -> Unit, remove: () -> Unit, dismiss: () -> Unit) {
    val stops = rememberTvDialogStops(2 + (if (configurable) 1 else 0) + 1)
    val first = stops.first()
    val removeStop = stops[stops.size - 2]
    val dismissStop = stops.last()
    TvDialogPanel(scale, "Manage ${addon.name}", stops, first, dismiss, actions = {
        TvDialogAction(scale, if (enabled) "Disable" else "Enable", first, enabled = !busy) { toggle(!enabled) }
        if (configurable) {
            TvDialogAction(scale, "Reconfigure", stops[stops.size - 2], enabled = !busy) { reconfigure() }
        }
        TvDialogAction(scale, "Remove addon", removeStop, danger = true, enabled = !busy) { remove() }
        TvDialogAction(scale, "Cancel", dismissStop, enabled = true) { dismiss() }
    }) {
        Text(addon.providesSummary().ifBlank { "This add-on is installed for this account." },
            color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
    }
}

@Composable
internal fun TvAddonRemoveDialog(addon: IntegrationConnection, local: Boolean, busy: Boolean, pending: Boolean, scale: Float,
    confirm: () -> Unit, dismiss: () -> Unit) {
    val stops = rememberTvDialogStops(2)
    val dismissStop = stops.first()
    val removeStop = stops.last()
    TvDialogPanel(scale, "Remove ${addon.name}?", stops, dismissStop, dismiss, actions = {
        TvDialogAction(scale, "Cancel", dismissStop, enabled = !busy) { dismiss() }
        TvDialogAction(scale, if (pending) "Removing…" else "Remove", removeStop, danger = true, enabled = !pending) { confirm() }
    }) {
        Text(if (local) "Its catalogs leave this device." else "This removes the add-on from this account and its profiles.",
            color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
    }
}

@Composable
internal fun TvAddonConfigureDialog(request: AddonConfigureRequest, scale: Float, dismiss: () -> Unit, newCode: () -> Unit) {
    val stops = rememberTvDialogStops(2)
    val newStop = stops.first()
    val closeStop = stops.last()
    val link = request.link
    TvDialogPanel(scale, "Reconfigure ${request.name}", stops, if (link == null) closeStop else newStop, dismiss, actions = {
        if (link != null) TvDialogAction(scale, "New code", newStop) { newCode() }
        TvDialogAction(scale, "Close", closeStop) { dismiss() }
    }) {
        if (link == null) {
            Text(request.failure ?: "Getting the configure link…", color = if (request.failure != null) Palette.danger else Palette.secondary,
                fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        } else {
            Text("Scan this code with a phone or computer camera to change ${request.name}’s settings.",
                color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
            Spacer(Modifier.height((28 * scale).dp))
            Box(Modifier.size((400 * scale).dp).background(Color.White, RoundedCornerShape((20 * scale).dp)), contentAlignment = Alignment.Center) {
                Image(rememberQrCodePainter(link) { this.scale = .88f }, "Configure QR code", Modifier.size((360 * scale).dp))
            }
            Spacer(Modifier.height((20 * scale).dp))
            Text(link, color = Palette.text, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height((8 * scale).dp))
            Text("This code expires in ${request.expiresInSeconds / 60} minutes.", color = Palette.secondary,
                fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        }
    }
}

internal fun addonConfigureFailureText(request: AddonConfigureRequest?): String? =
    request?.failure ?: request?.takeIf { it.link == null }?.let { ADDON_CONFIGURE_FAILURE }