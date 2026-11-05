package org.viptv.app.features.details

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.AppTextField
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.requestPlacedFocus
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import org.viptv.app.connection.seasonNumber

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
    val wanted = season.seasonNumber()
    return episodes.indexOfFirst { card ->
        val coordinates = card.identity.episode
        coordinates != null && coordinates.seasonNumber() == wanted && coordinates.episode == number
    }.takeIf { it >= 0 }
}

internal class EpisodeJumpTarget(val key: String, val index: Int)

@Composable
internal fun EpisodeJumpDialog(dismiss: () -> Unit, submit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val editor = remember { FocusRequester() }
    fun go() {
        if (submit(text)) return
        failed = true
        if (editing) editor.requestPlacedFocus() else field.requestPlacedFocus()
    }
    LaunchedEffect(Unit) {
        repeat(TV_FOCUS_RETRY_FRAMES) {
            if (field.requestPlacedFocus()) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        repeat(TV_FOCUS_RETRY_FRAMES) {
            if (editor.requestPlacedFocus()) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(EPISODE_JUMP_TITLE) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (editing) AppTextField(text, { text = it.take(EPISODE_JUMP_INPUT_LIMIT); failed = false },
                Modifier.fillMaxWidth().focusRequester(editor).semantics { if (failed) error(EPISODE_JUMP_ERROR) },
                label = EPISODE_JUMP_FIELD_LABEL, isError = failed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }), onEscape = { editing = false })
            else EpisodeJumpField(text, failed, field, activate = { editing = true })
            if (failed) Text(EPISODE_JUMP_ERROR, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = Palette.danger)
        } },
        confirmButton = { TextButton(::go) { Text(EPISODE_JUMP_GO) } },
        dismissButton = { TextButton(dismiss) { Text(EPISODE_JUMP_CANCEL) } })
}

@Composable
private fun EpisodeJumpField(text: String, failed: Boolean, requester: FocusRequester, activate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val borderColor = when {
        failed -> Palette.danger
        focused -> Palette.text
        else -> Color.White.copy(alpha = .14f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(EPISODE_JUMP_FIELD_LABEL, color = if (failed) Palette.danger else Palette.secondary, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .focusRequester(requester).onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { contentDescription = EPISODE_JUMP_FIELD_LABEL }
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = activate)
            .padding(horizontal = 12.dp, vertical = 16.dp)) {
            if (text.isNotEmpty()) Text(text, color = Palette.text, fontSize = 16.sp)
        }
    }
}
