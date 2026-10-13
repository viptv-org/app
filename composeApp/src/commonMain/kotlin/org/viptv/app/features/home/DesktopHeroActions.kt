package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.*
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip

@Composable
internal fun DesktopHeroActions(play: () -> Unit, info: () -> Unit, save: () -> Unit, saved: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(play, Modifier.width(140.dp).height(44.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
            Text("Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(info, Modifier.height(44.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
            Icon(Lucide.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("More info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        FilledIconButton(onClick = save, modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
            Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp))
        }
    }
}
