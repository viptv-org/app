package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.player.*
import org.viptv.app.theme.Palette

internal fun subtitleTimingLabel(ui: SubtitleSyncUiState): String {
    val seconds = ui.manualDelayUs / 100_000 / 10.0
    return "Subtitle timing (${if (seconds > 0) "+" else ""}$seconds s)"
}

internal fun subtitleTimingStatus(ui: SubtitleSyncUiState): String = when {
    ui.manual -> "Manual timing"
    !ui.automatic -> "Original timing"
    !ui.localAudioAvailable || ui.availability == SubtitleSyncAvailability.Unavailable -> "Automatic timing unavailable"
    ui.corrected -> "Automatic timing (prototype)"
    ui.availability == SubtitleSyncAvailability.Observing || ui.availability == SubtitleSyncAvailability.Accepted -> "Checking automatic timing"
    else -> "Automatic timing unavailable"
}

@Composable
internal fun SubtitleTimingChoices(session: SubtitleSyncSession,
    textScale: Float = 1f,
    row: @Composable (String, Boolean?, () -> Unit) -> Unit) {
    val ui by session.ui.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(subtitleTimingLabel(ui), fontSize = (14 * textScale).sp)
        Text(subtitleTimingStatus(ui), color = Palette.secondary, fontSize = (14 * textScale).sp)
        if (!ui.providerSelected) return@Column
        if (ui.localAudioAvailable || ui.automatic) {
            Text("Automatic timing (experimental)", Modifier.padding(top = 8.dp), fontSize = (14 * textScale).sp)
            row("Off", !ui.automatic) { session.setAutomatic(false) }
            if (ui.localAudioAvailable) row("On", ui.automatic) { session.setAutomatic(true) }
        } else Text("Automatic timing unavailable", color = Palette.secondary, fontSize = (14 * textScale).sp)
        row("Earlier by 0.5 s", null) { session.adjustManual(-500_000) }
        row("Later by 0.5 s", null) { session.adjustManual(500_000) }
        row("Reset automatic timing", null) { session.resetTiming() }
    }
}
