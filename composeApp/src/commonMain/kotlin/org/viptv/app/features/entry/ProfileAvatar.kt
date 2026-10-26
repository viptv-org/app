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

/** The profile's identity colour: the fill of an initial avatar, stable for a profile id. */
internal fun profileColor(profile: Profile?): Color = when (profile?.avatarId) {
    "initial-pink" -> Color(0xFFC24E6B)
    "initial-blue" -> Color(0xFF3E6B8A)
    else -> listOf(Color(0xFFC24E6B), Color(0xFF3E6B8A), Color(0xFF675C8E), Color(0xFF40766B))
        .let { it[((profile?.id?.hashCode() ?: 0) and Int.MAX_VALUE) % it.size] }
}

/**
 * A profile's avatar: its catalog pick from the bundled images first, then a legacy
 * bundled id, then the server's `avatarUrl`, then the initial on [profileColor].
 */
@Composable
internal fun ProfileAvatar(profile: Profile?, size: Dp, radius: Dp) {
    val pick = profile?.avatarPick()
    val catalog = if (pick != null) rememberAvatarCatalog() else null
    val packaged = pick?.let { catalog?.avatarUri(it.style, it.choice) }
    // Until the catalog has loaded, keep the image tile rather than flash an initial.
    val waiting = pick != null && catalog == null
    val avatarId = profile?.avatarId
    val artwork = when (avatarId) {
        "vynxc" -> Res.drawable.avatar_vynxc
        "zayne" -> Res.drawable.avatar_zayne
        else -> null
    }
    val remote = profile?.avatarUrl ?: profile?.avatar
    val image = packaged != null || waiting || artwork != null || remote != null
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(if (image) Color(0xFF0E0E10) else profileColor(profile)),
        contentAlignment = Alignment.Center) {
        when {
            packaged != null -> AsyncImage(packaged, profile?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            waiting -> Unit
            artwork != null -> androidx.compose.foundation.Image(painterResource(artwork), profile?.name,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            remote != null -> AsyncImage(remote, profile?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Text(profile?.name?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty(),
                fontSize = if (size <= 40.dp) (size.value * 0.57f).sp else (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
