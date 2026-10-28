package org.viptv.app.features.library

import org.viptv.app.components.FocusLazyColumn as LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
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
import org.viptv.app.components.pointerClickable

/** TV My List filters: a right-hand panel applied as a whole. Other layouts use [LibraryFilterBar]. */
@Composable
internal fun TvLibraryFilterSheet(query: LibraryQuery, controls: List<QueryControl>,
    scale: Float, close: () -> Unit, apply: (LibraryQuery) -> Unit) {
    var draft by remember(query) { mutableStateOf(query.values()) }
    val first = remember { FocusRequester() }
    val motion = rememberTvDrawerMotion(close)
    val fields = controls.filter { it.id !in listOf("source", "type") }
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.progress.value }.background(Color.Black.copy(alpha = .55f))
                .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = motion.dismiss))
            Surface(Modifier.align(Alignment.CenterEnd).width(800.dp * scale).fillMaxHeight()
                .graphicsLayer { translationX = size.width * (1f - motion.progress.value) }, color = Palette.surface) {
                Column(Modifier.padding(24.dp * scale), verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                    if (fields.isNotEmpty()) TvRequestedFocus(first, first)
                    Text("Filters", fontSize = (32 * scale).sp)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp * scale)) {
                        itemsIndexed(fields, key = { _, control -> control.id }) { index, control ->
                            TvLibraryFilterField(control, draft[control.id].orEmpty(), { draft = draft + (control.id to it) },
                                scale, if (index == 0) first else null)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        TvAction(Modifier.weight(1f).height(64.dp * scale), scale, activate = { draft = query.clearedFilters().values() }) { Text("Clear", color = it, fontSize = (24 * scale).sp) }
                        TvAction(Modifier.weight(1f).height(64.dp * scale), scale, activate = { apply(query.withValues(draft)); motion.dismiss() }) { Text("Apply", color = it, fontSize = (24 * scale).sp) }
                    }
                }
            }
        }
    }
}
