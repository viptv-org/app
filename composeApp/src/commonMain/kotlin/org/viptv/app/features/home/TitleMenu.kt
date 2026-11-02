package org.viptv.app.features.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rewind
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import org.viptv.app.components.ListRemoval
import org.viptv.app.components.hoverFill
import org.viptv.app.components.isWatched
import org.viptv.app.components.motionSpec
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pressScale
import org.viptv.app.components.rememberEnterLeaveMotion
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.components.Motion
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

internal const val RESUME_PREVIOUS_LABEL = "Resume previous episode"
internal const val CHOOSE_SOURCE_LABEL = "Choose source"
internal const val MARK_WATCHED_LABEL = "Mark watched"
internal const val MARK_UNWATCHED_LABEL = "Mark unwatched"
internal const val WATCH_FROM_START_LABEL = "Watch from the beginning"
internal const val CANCEL_LABEL = "Cancel"

// A movie or a specific episode has one source and one watch state; a live channel or a bare series does not.
internal fun Card.offersTitleActions(): Boolean = identity.kind == "movie" || identity.episode != null

internal fun Card.resumesPreviousEpisode(removal: ListRemoval?): Boolean =
    removal?.continueWatching == true && identity.episode != null && progress != null

@Composable
internal fun CardMenu(card: Card, saved: Boolean, removal: ListRemoval?, actions: CardActions?,
    onDismiss: () -> Unit, onPlay: () -> Unit, onInfo: () -> Unit, onToggleSave: (() -> Unit)?,
    visible: Boolean = true, onClosed: () -> Unit = {}) {
    val toggleSave = onToggleSave?.takeIf { removal?.replacesSave != true }
    val previous = card.resumesPreviousEpisode(removal)
    val watched = card.isWatched()
    val motion = rememberEnterLeaveMotion(visible,
        enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
    LaunchedEffect(motion.presented) { if (!motion.presented) onClosed() }
    Column(Modifier.width(300.dp)
        .graphicsLayer {
            shape = RoundedCornerShape(10.dp); shadowElevation = 16.dp.toPx()
            alpha = motion.progress
            val s = 0.98f + 0.02f * motion.progress
            scaleX = s; scaleY = s
        }
        .tvBackdropBlur(30.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF242428).copy(alpha = .97f))
        .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(10.dp))
        .semantics { contentDescription = "${card.title} actions" }
        .padding(5.dp)) {
        MenuItem(if (previous) RESUME_PREVIOUS_LABEL else if (card.progress != null) "Resume" else "Play",
            if (previous) Lucide.History else Lucide.Play, onDismiss, onPlay)
        val titled = card.offersTitleActions()
        actions?.chooseSource?.takeIf { titled }?.let { choose ->
            MenuItem(CHOOSE_SOURCE_LABEL, Lucide.Server, onDismiss, action = { choose(card) })
        }
        actions?.markWatched?.takeIf { titled }?.let { mark ->
            MenuItem(if (watched) MARK_UNWATCHED_LABEL else MARK_WATCHED_LABEL,
                if (watched) Lucide.Circle else Lucide.CircleCheck, onDismiss, action = { mark(card) })
        }
        actions?.playFromStart?.takeIf { titled }?.let { restart ->
            MenuItem(WATCH_FROM_START_LABEL, Lucide.Rewind, onDismiss, action = { restart(card) })
        }
        if (toggleSave != null || removal != null) {
            MenuDivider()
            if (toggleSave != null) MenuItem(if (saved) "In My List" else "Add to My List",
                if (saved) Lucide.Check else Lucide.Plus, onDismiss, toggleSave)
            if (removal != null) MenuItem(removal.label, Lucide.Trash2, onDismiss, action = { removal.request(card) }, danger = true)
        }
        MenuDivider()
        MenuItem("More info", Lucide.Info, onDismiss, onInfo)
        MenuItem(CANCEL_LABEL, Lucide.X, onDismiss, action = {})
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(1.dp)
        .background(Color.White.copy(alpha = .08f)))
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, dismiss: () -> Unit, action: () -> Unit, danger: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(34.dp).pressScale(interaction, Motion.row)
        // Draw the ring before the clip on the menu's rows, otherwise the clip cuts it.
        .onFocusChanged { focused = it.isFocused }
        .tvFocusOutline(focused, 6.dp, darkGap = 2.dp, width = 2.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(hoverFill(interaction, Color.Transparent, Color.White.copy(alpha = .12f)))
        .hoverable(interaction)
        .pointerClickable(interactionSource = interaction, indication = null, onClickLabel = label, shape = RoundedCornerShape(6.dp), hover = null) { dismiss(); action() }
        .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Palette.danger else Palette.text)
        Text(label, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            color = if (danger) Palette.danger else Palette.text)
    }
}
