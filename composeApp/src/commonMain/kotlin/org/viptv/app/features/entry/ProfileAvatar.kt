package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.connection.Profile
import org.viptv.app.resources.Res
import org.viptv.app.resources.avatar_vynxc
import org.viptv.app.resources.avatar_zayne

@Composable
internal fun ProfileAvatar(profile: Profile?, size: Dp, radius: Dp) {
    val avatarId = profile?.avatarId
    val color = when (avatarId) {
        "initial-pink" -> Color(0xFFC24E6B)
        "initial-blue" -> Color(0xFF3E6B8A)
        else -> listOf(Color(0xFFC24E6B), Color(0xFF3E6B8A), Color(0xFF675C8E), Color(0xFF40766B))
            .let { it[((profile?.id?.hashCode() ?: 0) and Int.MAX_VALUE) % it.size] }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(color), contentAlignment = Alignment.Center) {
        val artwork = when (avatarId) {
            "vynxc" -> Res.drawable.avatar_vynxc
            "zayne" -> Res.drawable.avatar_zayne
            else -> null
        }
        when {
            artwork != null -> androidx.compose.foundation.Image(painterResource(artwork), profile?.name,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            profile?.avatar != null -> AsyncImage(profile.avatar, profile.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Text(profile?.name?.take(1)?.uppercase().orEmpty(),
                fontSize = if (size <= 72.dp) 38.sp else 54.sp, fontWeight = FontWeight.Bold)
        }
    }
}
