package org.viptv.app.features.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.*
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.jetbrains.compose.resources.painterResource
import org.viptv.app.resources.Res
import org.viptv.app.resources.ic_play_zip
import org.viptv.app.components.Button

@Composable
internal fun DesktopHeroActions(play: () -> Unit, info: () -> Unit, save: () -> Unit, saved: Boolean) {
    // DUI-06: every hero action needs hover *and* keyboard focus. Material draws
    // a hover state layer; the ring is the board's `0 0 0 2px #0B0B0C, 0 0 0 4px
    // #F4F2EE`, drawn through tvFocusOutline so it is paint-only (PERF-02).
    val playInteractions = remember { MutableInteractionSource() }
    val playHovered by playInteractions.collectIsHoveredAsState()
    val playFocused by playInteractions.collectIsFocusedAsState()
    val infoInteractions = remember { MutableInteractionSource() }
    val infoHovered by infoInteractions.collectIsHoveredAsState()
    val infoFocused by infoInteractions.collectIsFocusedAsState()
    val saveInteractions = remember { MutableInteractionSource() }
    val saveHovered by saveInteractions.collectIsHoveredAsState()
    val saveFocused by saveInteractions.collectIsFocusedAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Light pill: board hover is pure #FFFFFF over #F4F2EE.
        Button(play, Modifier.width(140.dp).height(44.dp)
            .tvFocusOutline(playFocused, 12.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = playInteractions, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (playHovered) Color.White else Palette.text, contentColor = Palette.background)) {
            Icon(painterResource(Res.drawable.ic_play_zip), null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
            Text("Play", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        // Secondary: board hover lifts #212124 to #34343A.
        Button(info, Modifier.height(44.dp)
            .tvFocusOutline(infoFocused, 12.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = infoInteractions, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (infoHovered) Color(0xFF34343A) else Palette.raised, contentColor = Palette.text)) {
            Icon(Lucide.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("More info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        FilledIconButton(onClick = save, modifier = Modifier.size(44.dp)
            .tvFocusOutline(saveFocused, 22.dp, darkGap = 2.dp, width = 2.dp),
            interactionSource = saveInteractions,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (saveHovered) Color(0xFF34343A) else Palette.raised, contentColor = Palette.text)) {
            Icon(if (saved) Lucide.Check else Lucide.Plus, if (saved) "Remove from My List" else "Add to My List", Modifier.size(22.dp))
        }
    }
}
