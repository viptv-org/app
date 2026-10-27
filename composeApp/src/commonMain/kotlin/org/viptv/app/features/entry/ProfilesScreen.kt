package org.viptv.app.features.entry

import org.viptv.app.components.FocusLazyRow as LazyRow
import com.composables.icons.lucide.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.TextButton

@Composable
fun ProfilesScreen(
    profiles: List<Profile>, busy: Boolean, error: String?, local: Boolean, managing: Boolean, compact: Boolean, desktop: Boolean,
    select: (String) -> Unit, edit: (String) -> Unit, manage: () -> Unit, done: () -> Unit,
    create: (String) -> Unit, television: Boolean = false, back: (() -> Unit)? = null,
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    if (television) TvProfiles(profiles, busy, error, local, managing, select, edit, manage, done) { adding = true }
    else if (compact) MobileProfiles(profiles, busy, error, local, managing, select, edit, manage, done, back = back) { adding = true }
    else if (desktop) DesktopProfiles(profiles, busy, error, local, managing, select, edit, manage, done) { adding = true }
    else
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (managing) "Manage profiles" else "Who’s watching?", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(44.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(24.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Column(Modifier.pointerClickable(enabled = !busy) { if (managing) edit(profile.id) else select(profile.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)), color = Palette.raised) {
                        Box(contentAlignment = Alignment.Center) {
                            if (profile.avatar != null) AsyncImage(profile.avatar, profile.name, Modifier.fillMaxSize())
                            else Text(profile.name.take(1), fontSize = 40.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp)); Text(profile.name, color = Palette.secondary)
                    if (profile.locked && !managing) Text("Locked", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (local && profiles.size < 12) item {
                Column(Modifier.pointerClickable(enabled = !busy) { adding = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)), color = Palette.raised) {
                        Box(contentAlignment = Alignment.Center) { Icon(Lucide.Plus, "Add profile", Modifier.size(40.dp)) }
                    }
                    Spacer(Modifier.height(14.dp)); Text("Add profile", color = Palette.secondary)
                }
            }
        }
        if (local) TextButton(onClick = if (managing) done else manage, enabled = !busy) {
            Text(if (managing) "Done" else "Manage profiles")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add profile") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { create(name); adding = false; name = "" }, enabled = name.isNotBlank() && !busy) { Text("Add") } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } })
}
