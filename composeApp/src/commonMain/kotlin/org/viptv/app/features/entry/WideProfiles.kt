package org.viptv.app.features.entry

import org.viptv.app.components.FocusLazyRow as LazyRow
import com.composables.icons.lucide.*

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.dashedOutline
import org.viptv.app.components.pointerClickable
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

private val TileSize = 150.dp
private val TileRadius = 12.dp
private val TileGap = 28.dp

@Composable
internal fun WideProfiles(
    profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit, add: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tiles = profiles.size + if (local) 1 else 0
        val row = TileSize * tiles + TileGap * maxOf(0, tiles - 1)
        val side = maxOf(0.dp, (maxWidth - row) / 2)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(if (managing) "Manage profiles" else "Who’s watching?", fontSize = 44.sp, lineHeight = 51.sp,
                letterSpacing = (-0.88).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(profile, busy = busy, open = { if (managing) edit(profile.id) else select(profile.id) })
                }
                if (local) item(key = "add") { AddProfileTile(canAdd = !busy && profiles.size < ProfileLimit, add = add) }
            }
            Spacer(Modifier.height(40.dp))
            if (local) ManageProfilesButton(if (managing) "Done" else "Manage profiles", enabled = !busy,
                run = if (managing) done else manage)
            error?.let { Text(it, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ProfileTile(profile: Profile, busy: Boolean, open: () -> Unit) {
    Column(Modifier.width(TileSize).pointerClickable(enabled = !busy, role = Role.Button,
        shape = RoundedCornerShape(TileRadius), onClick = open), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileAvatar(profile, TileSize, TileRadius)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.width(TileSize), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (profile.locked) {
                LockMark(tint = Palette.secondary, size = 15.dp)
                Spacer(Modifier.width(5.dp))
            }
            Text(profile.name, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Palette.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AddProfileTile(canAdd: Boolean, add: () -> Unit) {
    val outline = if (canAdd) Color.White.copy(alpha = .22f) else Color.White.copy(alpha = .10f)
    Column(Modifier.width(TileSize).pointerClickable(enabled = canAdd, role = Role.Button,
        shape = RoundedCornerShape(TileRadius), onClick = add), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(TileSize).clip(RoundedCornerShape(TileRadius)).dashedOutline(outline, 2.dp, TileRadius),
            contentAlignment = Alignment.Center) {
            Icon(Lucide.Plus, "Add profile", Modifier.size(40.dp), tint = if (canAdd) Palette.secondary else Palette.tertiary)
        }
        Spacer(Modifier.height(12.dp))
        Text("Add profile", color = if (canAdd) Palette.secondary else Palette.tertiary, fontSize = 17.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ManageProfilesButton(label: String, enabled: Boolean, run: () -> Unit) {
    Row(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
        .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp))
        .pointerClickable(enabled = enabled, role = Role.Button, shape = RoundedCornerShape(12.dp), onClick = run)
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) Palette.text else Palette.tertiary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
