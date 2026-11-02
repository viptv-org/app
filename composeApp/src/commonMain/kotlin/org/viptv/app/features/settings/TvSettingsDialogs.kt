package org.viptv.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvLeaveConfirmation(local: Boolean, scale: Float, confirm: () -> Unit, dismiss: () -> Unit) {
    val initial = remember { FocusRequester() }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
            Column(Modifier.offset(y = (-57).dp * scale).width(800.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Color(0xFF202023))
                .padding(horizontal = 48.dp * scale, vertical = 44.dp * scale)) {
                Text(if (local) changeConnectionTitle else "Sign out of this TV?", fontSize = (44 * scale).sp, lineHeight = (53 * scale).sp,
                    letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp * scale))
                Text(if (local) changeConnectionBody(television = true) else "Your profiles, My List and progress stay on your account.",
                    fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp, color = Palette.secondary)
                Spacer(Modifier.height(36.dp * scale))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.width((if (local) 360.dp else 260.dp) * scale).height(72.dp * scale), scale, primary = true, ring = false, requester = initial, activate = confirm) {
                        Icon(Lucide.LogOut, null, Modifier.size(28.dp * scale), Color(0xFFCE211D)); Spacer(Modifier.width(12.dp * scale))
                        Text(if (local) changeConnectionAction else "Sign out", color = Color(0xFFCE211D), fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.width(220.dp * scale).height(72.dp * scale), scale, ring = false, activate = dismiss) {
                        Text("Cancel", color = it, fontSize = (26 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}

internal data class TvSettingsChoice(val title: String, val values: List<String>, val select: (String) -> Unit)

@Composable
internal fun TvSettingsChoiceDialog(choice: TvSettingsChoice, scale: Float, dismiss: () -> Unit) {
    val initial = remember { FocusRequester() }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(600.dp * scale).background(Palette.surface, RoundedCornerShape(24.dp * scale)).padding(40.dp * scale),
                verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                Text(choice.title, fontSize = (36 * scale).sp, fontWeight = FontWeight.Bold)
                choice.values.forEachIndexed { index, value ->
                    TvAction(Modifier.fillMaxWidth().height(64.dp * scale), scale, ring = false, requester = if (index == 0) initial else null, activate = { choice.select(value); dismiss() }) {
                        Text(value, color = it, fontSize = (26 * scale).sp)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}
