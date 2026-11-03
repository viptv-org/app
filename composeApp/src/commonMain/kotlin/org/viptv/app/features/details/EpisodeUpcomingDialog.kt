package org.viptv.app.features.details

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import org.viptv.app.components.TextButton
import org.viptv.engine.wire.Card

@Composable
internal fun EpisodeUpcomingDialog(episode: Card, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(episode.title, fontWeight = FontWeight.SemiBold) },
        text = { Text(EPISODE_UPCOMING_NOTICE) },
        confirmButton = { TextButton(onClick = dismiss) { Text("OK") } })
}