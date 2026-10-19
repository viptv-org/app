package org.viptv.app.features.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.components.*
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.QueryControl

/** Draft selections are applied together so a checkbox press never issues a catalog request. */
@Composable
internal fun DiscoveryMultiChoiceSheet(control: QueryControl, selected: List<String>, television: Boolean,
    scale: Float, close: () -> Unit, apply: (List<String>) -> Unit) {
    var draft by remember(control, selected) { mutableStateOf(selected.distinct()) }
    var committing by remember { mutableStateOf(false) }
    val maximum = (control.maximum_selections ?: 64L).coerceIn(0L, 64L).toInt()
    val choices = control.choices.distinctBy { it.value }
    val initialTarget = remember(control, selected) { choices.indexOfFirst { it.value in selected || selected.size < maximum } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialTarget.coerceAtLeast(0))
    val first = remember { FocusRequester() }
    val motion = rememberTvDrawerMotion(close)
    val valid = (!control.required || draft.isNotEmpty()) && draft.size <= maximum
    val confirm = { if (valid && !committing) { committing = true; apply(draft); motion.dismiss() } }
    Popup(onDismissRequest = motion.dismiss, properties = PopupProperties(focusable = true)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val phone = maxWidth < 640.dp && !television
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = motion.progress.value }
                .background(Color.Black.copy(alpha = .55f)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = motion.dismiss))
            val panel = if (phone) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.9f)
                else Modifier.align(Alignment.CenterEnd).width(if (television) 800.dp * scale else 480.dp).fillMaxHeight()
            Surface(panel.graphicsLayer {
                if (phone) translationY = size.height * (1f - motion.progress.value)
                else translationX = size.width * (1f - motion.progress.value)
            }, color = if (phone) Color(0xFF1A1A1D) else Palette.surface,
                shape = if (phone) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(0.dp)) {
                Column(Modifier.fillMaxSize().padding(24.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    Text(control.label, fontSize = (if (television) 32 * scale else 24f).sp)
                    Text(if (control.required) "Choose at least one · up to $maximum" else "Select up to $maximum",
                        color = Palette.secondary, fontSize = (if (television) 20 * scale else 14f).sp)
                    if (television) TvRequestedFocus(first, first)
                    LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                        itemsIndexed(choices, key = { _, choice -> choice.value }) { index, choice ->
                            val checked = choice.value in draft
                            val toggle = { draft = if (checked) draft - choice.value else if (draft.size < maximum) draft + choice.value else draft }
                            if (television) TvAction(Modifier.fillMaxWidth().height(64.dp * scale), scale,
                                requester = if (index == initialTarget) first else null, enabled = checked || draft.size < maximum, activate = toggle) {
                                Icon(if (checked) Lucide.SquareCheck else Lucide.Square, null, Modifier.size(24.dp * scale), tint = it)
                                Spacer(Modifier.width(12.dp * scale)); Text(choice.label, color = it, fontSize = (24 * scale).sp)
                            } else Row(Modifier.fillMaxWidth().clickable(enabled = checked || draft.size < maximum, onClick = toggle).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { toggle() }, enabled = checked || draft.size < maximum)
                                Text(choice.label, Modifier.weight(1f))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        if (television) {
                            TvAction(Modifier.weight(1f).height(64.dp * scale), scale, requester = if (initialTarget < 0) first else null,
                                activate = { draft = emptyList() }) { Text("Clear", color = it, fontSize = (24 * scale).sp) }
                            TvAction(Modifier.weight(1f).height(64.dp * scale), scale, enabled = valid, activate = confirm) { Text("Apply", color = it, fontSize = (24 * scale).sp) }
                        } else {
                            TextButton(onClick = { draft = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear") }
                            Button(onClick = confirm, enabled = valid, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text("Apply") }
                        }
                    }
                }
            }
        }
    }
}
