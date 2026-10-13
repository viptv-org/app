package org.viptv.app.features.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.viptv.app.components.TvAction
import org.viptv.app.components.TvRequestedFocus
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.QueryControl

@Composable
internal fun LibraryFilterDialog(query: LibraryQuery, controls: List<QueryControl>, television: Boolean,
    scale: Float, close: () -> Unit, apply: (LibraryQuery) -> Unit) {
    var draft by remember(query) { mutableStateOf(query.values()) }
    val first = remember { FocusRequester() }
    val fields = controls.filter { it.id !in listOf("source", "type") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = (if (television) 720.dp * scale else 480.dp)).fillMaxWidth()
            .fillMaxHeight(.82f), color = Palette.surface, shape = RoundedCornerShape(16.dp * scale)) {
            Column(Modifier.padding(24.dp * scale), verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
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
                        TvAction(Modifier.weight(1f).height(64.dp * scale), scale, activate = { apply(query.withValues(draft)); close() }) { Text("Apply", color = it, fontSize = (24 * scale).sp) }
                    } else {
                        TextButton(onClick = { draft = query.copy(show = "all", sort = "last_watched", status = null, genre = null, rated = null, added = null, episodes_left_max = null).values() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                        Button(onClick = { apply(query.withValues(draft)); close() }, modifier = Modifier.weight(1f)) { Text("Apply") }
                    }
                }
            }
        }
    }
}
