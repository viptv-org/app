package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TvLeaveConfirmation(local: Boolean, scale: Float, confirm: () -> Unit, dismiss: () -> Unit) {
    TvConfirmDialog(scale, if (local) changeConnectionTitle else "Sign out of this TV?",
        if (local) changeConnectionBody(television = true) else "Your profiles and progress stay on your account.",
        confirm = if (local) changeConnectionAction else "Sign out", danger = true,
        onConfirm = confirm, onDismiss = dismiss)
}

internal data class TvSettingsChoice(val title: String, val values: List<String>, val select: (String) -> Unit)

@Composable
internal fun TvSettingsChoiceDialog(choice: TvSettingsChoice, scale: Float, dismiss: () -> Unit) {
    val stops = rememberTvDialogStops(choice.values.size + 1)
    val first = stops.first()
    TvDialogPanel(scale, choice.title, stops = stops, focus = first, dismiss = dismiss, actions = {
        TvDialogAction(scale, "Cancel", stop = first) { dismiss() }
    }) {
        choice.values.forEachIndexed { index, value ->
            TvDialogChip(value, selected = false, scale = scale, stop = stops[index + 1]) { choice.select(value); dismiss() }
            Spacer(Modifier.height((12 * scale).dp))
        }
    }
}
