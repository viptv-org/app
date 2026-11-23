package org.viptv.app.features.downloads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import org.viptv.app.components.PopoverMenu
import org.viptv.app.components.PopoverMenuDivider
import org.viptv.app.components.PopoverMenuItem
import org.viptv.app.components.pointerClickable
import org.viptv.app.downloads.DownloadItem
import org.viptv.app.downloads.DownloadState
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun EpisodeDownloadControl(card: Card, modifier: Modifier = Modifier) {
    val actions = LocalDownloadActions.current ?: return
    val item = LocalDownloadIndex.current[downloadKey(card)]
    DownloadControl(card, item, actions, modifier)
}

@Composable
internal fun DownloadControl(card: Card, item: DownloadItem?, actions: DownloadActions, modifier: Modifier = Modifier) {
    val requested = actions.requested(card)
    val state = item.controlState(requested)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.size(44.dp).pointerClickable(role = Role.Button, shape = CircleShape) {
            when (state) {
                DownloadControlState.None -> actions.title(card)
                DownloadControlState.Failed -> item?.let(actions::retry)
                DownloadControlState.Requested -> Unit
                else -> menu = true
            }
        }.semantics { contentDescription = controlName(card, item, requested) }, contentAlignment = Alignment.Center) {
            DownloadGlyph(state, item?.fraction, 24.dp)
        }
        if (item != null) DownloadItemMenu(item, actions, menu) { menu = false }
    }
}

@Composable
internal fun DownloadItemMenu(item: DownloadItem, actions: DownloadActions, expanded: Boolean, dismiss: () -> Unit) {
    val downloads = actions.downloads
    PopoverMenu(expanded, dismiss, paneTitle = episodeLabel(item.card)) {
        val close = { act: () -> Unit -> { dismiss(); act() } }
        when {
            item.state == DownloadState.Completed -> {
                PopoverMenuItem("Play", Lucide.Play, onClick = close { actions.play(item.id) })
                PopoverMenuItem(if (item.keep) "Don't keep" else "Keep", if (item.keep) Lucide.PinOff else Lucide.Pin,
                    onClick = close { downloads.keep(item.id, !item.keep) })
                PopoverMenuDivider()
                PopoverMenuItem("Delete download", Lucide.Trash2, onClick = close { downloads.delete(item.id) })
            }
            item.state == DownloadState.Failed -> {
                PopoverMenuItem("Try again", Lucide.RotateCw, onClick = close { actions.retry(item) })
                PopoverMenuItem("Remove", Lucide.Trash2, onClick = close { downloads.delete(item.id) })
            }
            else -> {
                if (item.state == DownloadState.Paused) PopoverMenuItem("Resume", Lucide.Play, onClick = close { downloads.resume(item.id) })
                else PopoverMenuItem("Pause", Lucide.Pause, onClick = close { downloads.pause(item.id) })
                PopoverMenuItem("Cancel download", Lucide.X, onClick = close { downloads.cancel(item.id) })
            }
        }
    }
}

@Composable
internal fun DownloadGlyph(state: DownloadControlState, fraction: Float?, size: Dp) {
    val accent = Palette.accent
    val track = Color.White.copy(alpha = .28f)
    when (state) {
        DownloadControlState.None -> Icon(Lucide.ArrowDownToLine, null, Modifier.size(size), tint = Palette.text)
        DownloadControlState.Completed -> Box(Modifier.size(size).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Lucide.Check, null, Modifier.size(size * .62f), tint = Palette.background)
        }
        DownloadControlState.Failed -> Icon(Lucide.CircleAlert, null, Modifier.size(size), tint = Palette.danger)
        DownloadControlState.Resolving -> {
            val spin = rememberInfiniteTransition(label = "download resolving")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "spin")
            Canvas(Modifier.size(size)) {
                val stroke = 2.dp.toPx()
                drawCircle(track, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
                drawArc(accent, angle - 90f, 90f, false, style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
            }
        }
        else -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = 2.dp.toPx()
                drawCircle(track, radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
                val sweep = if (state == DownloadControlState.Downloading || state == DownloadControlState.Halted) 360f * (fraction ?: 0f) else 0f
                if (sweep > 0f) drawArc(accent, -90f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
            }
            if (state == DownloadControlState.Halted) Icon(Lucide.Pause, null, Modifier.size(size * .46f), tint = Palette.text)
        }
    }
}
