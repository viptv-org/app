package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.viptv.app.components.rememberTvDrawerMotion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.QueryControl

@Composable
internal fun LibraryFilterSheet(query: LibraryQuery, controls: List<QueryControl>, television: Boolean,
    scale: Float, close: () -> Unit, apply: (LibraryQuery) -> Unit) {
    var draft by remember(query) { mutableStateOf(query.values()) }
    val first = remember { FocusRequester() }
    val motion = rememberTvDrawerMotion(close)
    val fields = controls.filter { it.id !in listOf("source", "type") }
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val phone = maxWidth < 640.dp && !television
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.progress.value }.background(Color.Black.copy(alpha = .55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = motion.dismiss))
        val panel = if (phone) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.9f)
            else Modifier.align(Alignment.CenterEnd).width(if (television) 800.dp * scale else 480.dp).fillMaxHeight()
        Surface(panel.graphicsLayer {
            if (phone) translationY = size.height * (1f - motion.progress.value)
            else translationX = size.width * (1f - motion.progress.value)
        }, color = Palette.surface, shape = if (phone) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(0.dp)) {
            Column(Modifier.padding(24.dp * scale), verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                if (television && fields.isNotEmpty()) TvRequestedFocus(first, first)
                Text("Filters", fontSize = (if (television) 32 * scale else 24f).sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                    itemsIndexed(fields, key = { _, control -> control.id }) { index, control ->
                        LibraryFilterField(control, draft[control.id].orEmpty(), { draft = draft + (control.id to it) },
                            television, scale, if (index == 0) first else null)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    if (television) {
                        TvAction(Modifier.weight(1f).height(64.dp * scale), scale, activate = { draft = query.copy(show = "all", sort = "last_watched", status = null, genre = null, rated = null, added = null, episodes_left_max = null).values() }) { Text("Clear", color = it, fontSize = (24 * scale).sp) }
                        TvAction(Modifier.weight(1f).height(64.dp * scale), scale, activate = { apply(query.withValues(draft)); motion.dismiss() }) { Text("Apply", color = it, fontSize = (24 * scale).sp) }
                    } else {
                        TextButton(onClick = { draft = query.copy(show = "all", sort = "last_watched", status = null, genre = null, rated = null, added = null, episodes_left_max = null).values() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                        Button(onClick = { apply(query.withValues(draft)); motion.dismiss() }, modifier = Modifier.weight(1f)) { Text("Apply") }
                    }
                }
            }
        }
        }
    }
}
