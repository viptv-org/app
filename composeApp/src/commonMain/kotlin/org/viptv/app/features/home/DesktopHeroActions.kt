package org.viptv.app.features.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.*
import org.viptv.app.components.HoverFill
import org.viptv.app.components.pointerHovered
import org.viptv.app.components.responseSpec
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.components.Button

@Composable
internal fun DesktopHeroActions(play: () -> Unit, info: () -> Unit, save: () -> Unit, saved: Boolean) {
    val playInteractions = remember { MutableInteractionSource() }
    val playFocused by playInteractions.collectIsFocusedAsState()
    val infoInteractions = remember { MutableInteractionSource() }
    val infoFocused by infoInteractions.collectIsFocusedAsState()
    val saveInteractions = remember { MutableInteractionSource() }
    val saveHovered = saveInteractions.pointerHovered()
    val saveFocused by saveInteractions.collectIsFocusedAsState()
    val saveFill by animateColorAsState(if (saveHovered) HoverFill.surface4 else Palette.raised, responseSpec(), label = "Save fill")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(play, Modifier.width(140.dp).height(44.dp)
            .tvFocusOutline(playFocused, 12.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = playInteractions, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
            Text("Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Button(info, Modifier.height(44.dp)
            .tvFocusOutline(infoFocused, 12.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = infoInteractions, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.raised, contentColor = Palette.text)) {
            Icon(Lucide.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("More info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        FilledIconButton(onClick = save, modifier = Modifier.size(44.dp)
            .tvFocusOutline(saveFocused, 22.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = saveInteractions,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = saveFill, contentColor = Palette.text)) {
            Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp))
        }
    }
}