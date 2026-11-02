package org.viptv.app.features.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.viptv.app.components.TextButton
import org.viptv.app.components.requestPlacedFocus
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val EPISODE_JUMP_CHIP_LABEL = "Episode #"
internal const val EPISODE_JUMP_CHIP_DESCRIPTION = "Jump to episode number"
internal const val EPISODE_JUMP_TITLE = "Jump to episode"
internal const val EPISODE_JUMP_FIELD_LABEL = "Episode number"
internal const val EPISODE_JUMP_GO = "Go"
internal const val EPISODE_JUMP_CANCEL = "Cancel"
internal const val EPISODE_JUMP_ERROR = "Episode not found in this season."
private const val EPISODE_JUMP_INPUT_LIMIT = 30

internal fun episodeJumpIndex(episodes: List<Card>, season: Long?, input: String): Int? {
    if (input.isEmpty() || !input.all { it in '0'..'9' }) return null
    val number = input.toLongOrNull() ?: return null
    val wanted = season ?: 1L
    return episodes.indexOfFirst { card ->
        val coordinates = card.identity.episode
        coordinates != null && (coordinates.season ?: 1L) == wanted && coordinates.episode == number
    }.takeIf { it >= 0 }
}

internal class EpisodeJumpTarget(val key: String, val index: Int)

@Composable
internal fun EpisodeJumpDialog(dismiss: () -> Unit, submit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    fun go() {
        if (submit(text)) return
        failed = true
        field.requestPlacedFocus()
    }
    LaunchedEffect(Unit) {
        repeat(TV_FOCUS_RETRY_FRAMES) {
            if (field.requestPlacedFocus()) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(EPISODE_JUMP_TITLE) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it.take(EPISODE_JUMP_INPUT_LIMIT); failed = false },
                Modifier.fillMaxWidth().focusRequester(field).semantics { if (failed) error(EPISODE_JUMP_ERROR) },
                label = { Text(EPISODE_JUMP_FIELD_LABEL) }, singleLine = true, isError = failed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }))
            if (failed) Text(EPISODE_JUMP_ERROR, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = Palette.danger)
        } },
        confirmButton = { TextButton(::go) { Text(EPISODE_JUMP_GO) } },
        dismissButton = { TextButton(dismiss) { Text(EPISODE_JUMP_CANCEL) } })
}
