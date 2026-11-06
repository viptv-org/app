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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import org.viptv.app.components.pointerClickable
import org.viptv.app.features.settings.TvDialogAction
import org.viptv.app.features.settings.TvDialogPanel
import org.viptv.app.features.settings.TvDialogStop
import org.viptv.app.features.settings.rememberTvDialogStop
import org.viptv.app.features.settings.tvDialogFocusWatch
import org.viptv.app.features.settings.tvDialogStop
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
internal fun EpisodeJumpDialog(scale: Float, dismiss: () -> Unit, submit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val field = rememberTvDialogStop()
    val editor = rememberTvDialogStop()
    val go = rememberTvDialogStop()
    fun run() {
        if (submit(text)) return
        failed = true
    }
    TvDialogPanel(scale, EPISODE_JUMP_TITLE, stops = if (editing) listOf(editor, go) else listOf(field, go),
        focus = if (editing) editor else field, dismiss = dismiss, actions = {
            TvDialogAction(scale, EPISODE_JUMP_CANCEL) { dismiss() }
            TvDialogAction(scale, EPISODE_JUMP_GO, stop = go) { run() }
        }) {
        if (editing) AppTextField(text, { text = it.take(EPISODE_JUMP_INPUT_LIMIT); failed = false },
            Modifier.tvDialogStop(editor).fillMaxWidth().semantics { if (failed) error(EPISODE_JUMP_ERROR) },
            label = EPISODE_JUMP_FIELD_LABEL, isError = failed,
            textStyle = LocalTextStyle.current.copy(fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { run() }), onEscape = { editing = false })
        else EpisodeJumpField(text, failed, scale, field, activate = { editing = true })
        if (failed) Text(EPISODE_JUMP_ERROR, Modifier.padding(top = (12 * scale).dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = Palette.danger, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp)
    }
}

@Composable
private fun EpisodeJumpField(text: String, failed: Boolean, scale: Float, stop: TvDialogStop, activate: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((10 * scale).dp)
    val borderColor = when {
        failed -> Palette.danger
        focused -> Palette.text
        else -> Color.White.copy(alpha = .14f)
    }
    Column(verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        Text(EPISODE_JUMP_FIELD_LABEL, color = if (failed) Palette.danger else Palette.secondary, fontSize = (22 * scale).sp)
        Box(Modifier.fillMaxWidth().heightIn(min = (88 * scale).dp).tvDialogFocusWatch(stop)
            .focusRequester(stop.requester).onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { contentDescription = EPISODE_JUMP_FIELD_LABEL }
            .border(if (focused) (2 * scale).dp else (1 * scale).dp, borderColor, shape)
            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = activate)
            .padding(horizontal = (20 * scale).dp, vertical = (24 * scale).dp)) {
            if (text.isNotEmpty()) Text(text, color = Palette.text, fontSize = (26 * scale).sp)
        }
    }
}
