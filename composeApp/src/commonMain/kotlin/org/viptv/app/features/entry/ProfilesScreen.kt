package org.viptv.app.features.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

@Composable
fun ProfilesScreen(profiles: List<Profile>, busy: Boolean, select: (String) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Who’s watching?", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(44.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(24.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Column(Modifier.clickable(enabled = !busy) { select(profile.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)), color = Palette.raised) {
                        Box(contentAlignment = Alignment.Center) {
                            if (profile.avatar != null) AsyncImage(profile.avatar, profile.name, Modifier.fillMaxSize())
                            else Text(profile.name.take(1), fontSize = 40.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp)); Text(profile.name, color = Palette.secondary)
                }
            }
        }
    }
}
