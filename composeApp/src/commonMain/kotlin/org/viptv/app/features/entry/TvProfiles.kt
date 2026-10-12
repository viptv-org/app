package org.viptv.app.features.entry

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

@Composable
internal fun TvProfiles(profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit, add: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(profiles.map { it.id }, managing) { if (profiles.isNotEmpty()) first.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        val cardSize = 200.dp * scale
        val count = profiles.size + if (local && profiles.size < 12) 1 else 0
        Text(if (managing) "Manage profiles" else "Who’s watching?",
            Modifier.align(Alignment.TopCenter).padding(top = 218.dp * scale),
            fontSize = (56 * scale).sp, letterSpacing = (-1.12f * scale).sp, lineHeight = (66 * scale).sp, fontWeight = FontWeight.Bold)
        LazyRow(Modifier.align(Alignment.TopCenter).padding(top = 380.dp * scale)
            .widthIn(max = cardSize * count + 56.dp * scale * (count - 1).coerceAtLeast(0)),
            horizontalArrangement = Arrangement.spacedBy(56.dp * scale), contentPadding = PaddingValues(horizontal = 4.dp * scale)) {
            itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                TvProfileItem(profile.name, profile.locked && !managing, cardSize, scale,
                    if (index == 0) Modifier.focusRequester(first) else Modifier, !busy,
                    { if (managing) edit(profile.id) else select(profile.id) }) {
                    ProfileAvatar(profile, cardSize, 28.dp * scale)
                }
            }
            if (local && profiles.size < 12) item {
                TvProfileItem("Add profile", false, cardSize, scale, Modifier, !busy, add) {
                    Surface(Modifier.size(cardSize), shape = RoundedCornerShape(28.dp * scale), color = Palette.raised) {
                        Box(contentAlignment = Alignment.Center) { Icon(Lucide.Plus, null, Modifier.size(56.dp * scale), tint = Palette.secondary) }
                    }
                }
            }
        }
        if (local) Button(onClick = if (managing) done else manage, enabled = !busy,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 780.dp * scale).height(72.dp * scale),
            shape = RoundedCornerShape(12.dp * scale),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
            Icon(Lucide.Pencil, null, Modifier.size(28.dp * scale)); Spacer(Modifier.width(12.dp * scale))
            Text(if (managing) "Done" else "Manage profiles", fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
        }
        error?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Palette.danger) }
    }
}

@Composable
private fun TvProfileItem(name: String, locked: Boolean, size: androidx.compose.ui.unit.Dp, scale: Float,
    modifier: Modifier, enabled: Boolean, choose: () -> Unit, artwork: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(size).onFocusChanged { focused = it.isFocused }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = choose),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.border(if (focused) 4.dp * scale else 0.dp, if (focused) Palette.text else androidx.compose.ui.graphics.Color.Transparent,
            RoundedCornerShape(28.dp * scale))) { artwork() }
        Row(Modifier.padding(top = 24.dp * scale), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
            if (locked) Icon(Lucide.Lock, null, Modifier.size(22.dp * scale), tint = Palette.secondary)
            Text(name, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, color = if (focused) Palette.text else Palette.secondary,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
        }
    }
}
