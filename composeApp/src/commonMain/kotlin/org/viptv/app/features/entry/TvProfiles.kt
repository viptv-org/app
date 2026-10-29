package org.viptv.app.features.entry

import org.viptv.app.components.FocusLazyRow as LazyRow
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.TvAction

@Composable
internal fun TvProfiles(profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit, add: () -> Unit) {
    val first = remember { FocusRequester() }; val manageFocus = remember { FocusRequester() }
    var leftTile by remember { mutableStateOf<FocusRequester?>(null) }
    val arrivals = remember { mutableMapOf<String, Animatable<Float, AnimationVector1D>>() }
    var firstPlaced by remember(profiles.firstOrNull()?.id, managing, local) { mutableStateOf(false) }
    LaunchedEffect(firstPlaced, managing, local, busy) {
        if (firstPlaced && !busy) {
            // LazyRow composes its children during layout, after the parent effect; wait for the enabled focus node to be attached before entering it.
            withFrameNanos { }
            first.requestFocus()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val cardSize = 200.dp * scale
        val count = profiles.size + if (local && profiles.size < 12) 1 else 0
        Text(if (managing) "Manage profiles" else "Who’s watching?",
            Modifier.align(Alignment.TopCenter).padding(top = 220.dp * scale),
            fontSize = (56 * scale).sp, letterSpacing = (-1.12f * scale).sp, lineHeight = (64.4f * scale).sp, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.align(Alignment.TopCenter).padding(top = 380.dp * scale)
            .widthIn(max = cardSize * count + 56.dp * scale * (count - 1).coerceAtLeast(0) + 8.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(56.dp * scale), contentPadding = PaddingValues(horizontal = 4.dp * scale)) {
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                val tile = remember { FocusRequester() }
                DisposableEffect(tile) { onDispose { if (leftTile === tile) leftTile = null } }
                TvProfileItem(profile.name, profile.locked && !managing, cardSize, scale,
                    (if (index == 0) Modifier.focusRequester(first).onGloballyPositioned { firstPlaced = true } else Modifier)
                        .focusRequester(tile).focusProperties { if (local) down = manageFocus }
                        .onFocusChanged { if (it.isFocused) leftTile = tile }
                        .then(tvProfileArrival("profile:${profile.id}", index, scale, arrivals)), !busy,
                    { if (managing) edit(profile.id) else select(profile.id) }) {
                    ProfileAvatar(profile, cardSize, 28.dp * scale)
                }
            }
            if (local && profiles.size < 12) item {
                val tile = remember { FocusRequester() }
                DisposableEffect(tile) { onDispose { if (leftTile === tile) leftTile = null } }
                TvProfileItem("Add profile", false, cardSize, scale, (if (profiles.isEmpty()) Modifier.focusRequester(first).onGloballyPositioned { firstPlaced = true } else Modifier)
                    .focusRequester(tile).focusProperties { down = manageFocus }
                    .onFocusChanged { if (it.isFocused) leftTile = tile }
                    .then(tvProfileArrival("add", profiles.size, scale, arrivals)), !busy, add) {
                    Surface(Modifier.size(cardSize).border(2.dp * scale, Color.White.copy(alpha = .18f), RoundedCornerShape(28.dp * scale)), shape = RoundedCornerShape(28.dp * scale), color = Color.White.copy(alpha = .08f)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Lucide.Plus, null, Modifier.size(64.dp * scale), tint = Palette.secondary) }
                    }
                }
            }
        }
        if (local) Box(Modifier.align(Alignment.TopCenter).padding(top = 780.dp * scale)) {
            TvAction(Modifier.width(300.dp * scale).height(72.dp * scale).focusProperties { up = leftTile ?: first }, scale,
                requester = manageFocus, activate = if (managing) done else manage, enabled = !busy, background = Color.White.copy(alpha = .16f)) {
                Icon(Lucide.Pencil, null, Modifier.size(28.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
                Text(if (managing) "Done" else "Manage profiles", color = it, fontSize = (26 * scale).sp,
                    lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Palette.danger) }
    }
}
