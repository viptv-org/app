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

// current is the label the row shows, so the list opens on the value already in use.
internal data class TvSettingsChoice(val title: String, val values: List<String>, val current: String?, val select: (String) -> Unit)

@Composable
internal fun TvSettingsChoiceDialog(choice: TvSettingsChoice, scale: Float, dismiss: () -> Unit) {
    val stops = rememberTvDialogStops(choice.values.size + 1)
    val checked = choice.values.indexOf(choice.current)
    val cancel = stops.last()
    TvDialogPanel(scale, choice.title, stops = stops, focus = stops.getOrElse(checked) { stops.first() }, dismiss = dismiss, actions = {
        TvDialogAction(scale, "Cancel", stop = cancel) { dismiss() }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            choice.values.forEachIndexed { index, value ->
                TvDialogChoiceRow(value, selected = index == checked, scale = scale, stop = stops[index]) { choice.select(value); dismiss() }
            }
        }
    }
}
