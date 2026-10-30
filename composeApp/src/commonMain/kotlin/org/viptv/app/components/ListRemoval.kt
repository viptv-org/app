package org.viptv.app.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

// A page whose cards can leave its list offers this; the page owns the confirmation and the request.
internal class ListRemoval(val label: String, val replacesSave: Boolean = false, val request: (Card) -> Unit)

internal val LocalListRemoval = compositionLocalOf<ListRemoval?> { null }

internal const val KEEP_HISTORY_COPY = "Watched history is preserved."

@Composable
internal fun ListRemovalConfirmation(card: Card, label: String, confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("$label?") },
        text = { Text("“${card.title}” leaves this list. $KEEP_HISTORY_COPY") },
        confirmButton = { TextButton(onClick = confirm) { Text("Remove", color = Palette.danger) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
