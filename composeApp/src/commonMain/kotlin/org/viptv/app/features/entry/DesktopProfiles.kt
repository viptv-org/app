package org.viptv.app.features.entry

import org.viptv.app.components.FocusLazyRow as LazyRow
import com.composables.icons.lucide.*

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.OwnFocusTreatment
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.OutlinedButton

@Composable
internal fun DesktopProfiles(
    profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit,
    add: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val count = profiles.size + if (local && profiles.size < 12) 1 else 0
        val width = 148.dp * count + 24.dp * maxOf(0, count - 1)
        val side = maxOf(0.dp, (maxWidth - width) / 2)
        Column(Modifier.fillMaxSize().padding(top = if (maxHeight >= 640.dp) 150.dp else 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (managing) "Manage profiles" else "Who’s watching?", fontSize = 30.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(32.dp))
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    DesktopProfileTile(profile, busy, profile.id == profiles.firstOrNull()?.id) {
                        if (managing) edit(profile.id) else select(profile.id)
                    }
                }
                if (local && profiles.size < 12) item(key = "add") { DesktopAddProfileTile(busy, add) }
            }
            Spacer(Modifier.height(40.dp))
            if (local) OutlinedButton(onClick = if (managing) done else manage, enabled = !busy,
                shape = RoundedCornerShape(12.dp)) {
                if (!managing) { androidx.compose.material3.Icon(Lucide.Pencil, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                Text(if (managing) "Done" else "Manage profiles", fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            error?.let { Text(it, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun DesktopProfileTile(profile: Profile, busy: Boolean, initialFocus: Boolean, open: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(profile.id, initialFocus) { if (initialFocus) focusRequester.requestFocus() }
    // The avatar border is the tile's focus treatment; Material's focus layer would paint a second, rectangular one around the column.
    OwnFocusTreatment { Column(Modifier.width(148.dp).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused }
        .pointerClickable(enabled = !busy, onClick = open), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(148.dp).border(2.dp, if (focused) Palette.text else Color.Transparent,
            RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
            ProfileAvatar(profile, 140.dp, 22.dp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (profile.locked) LockMark()
            Text(profile.name, fontSize = 15.sp, color = Palette.text, maxLines = 1)
        }
    } }
}

@Composable
private fun DesktopAddProfileTile(busy: Boolean, add: () -> Unit) {
    Column(Modifier.width(148.dp).pointerClickable(enabled = !busy, onClick = add),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(148.dp).padding(4.dp).border(2.dp, Color.White.copy(alpha = .14f),
            RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Lucide.Plus, "Add profile", Modifier.size(40.dp), tint = Palette.secondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Add profile", color = Palette.secondary, fontSize = 15.sp)
    }
}
