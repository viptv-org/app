package org.viptv.app.features.entry

import com.composables.icons.lucide.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import com.composables.icons.lucide.Lock

@Composable
internal fun MobileProfiles(
    profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit,
    add: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (local) TextButton(onClick = if (managing) done else manage, enabled = !busy) {
                Text(if (managing) "Done" else "Edit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(if (managing) "Manage profiles" else "Who’s watching?", Modifier.fillMaxWidth(),
            fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        error?.let { Text(it, Modifier.fillMaxWidth().padding(top = 8.dp, start = 24.dp, end = 24.dp),
            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(48.dp))
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp), horizontalArrangement = Arrangement.Center,
        ) {
            items(profiles, key = { it.id }) { profile ->
                MobileProfileTile(profile, busy) { if (managing) edit(profile.id) else select(profile.id) }
            }
            if (local && profiles.size < 12) item(key = "add") { AddProfileTile(busy, add) }
        }
    }
}

@Composable
private fun MobileProfileTile(profile: Profile, busy: Boolean, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = open), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileAvatar(profile, 120.dp, 22.dp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (profile.locked) LockMark()
            Text(profile.name, fontSize = 15.sp, lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun LockMark() {
    androidx.compose.material3.Icon(com.composables.icons.lucide.Lucide.Lock, "Locked", Modifier.size(14.dp), tint = Palette.text)
}

@Composable
private fun AddProfileTile(busy: Boolean, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = add), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Lucide.Plus, "Add profile", Modifier.size(40.dp), tint = Palette.secondary)
        }
        Spacer(Modifier.height(10.dp))
        Text("Add profile", color = Palette.secondary, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}
