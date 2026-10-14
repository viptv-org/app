package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

@Composable
internal fun TvPairingBrowser(session: TvPairingSession?, scale: Float, first: FocusRequester, busy: Boolean = false, refresh: () -> Unit) {
    Column(Modifier.offset(1064.dp * scale, 250.dp * scale).width(760.dp * scale), verticalArrangement = Arrangement.spacedBy(22.dp * scale)) {
        Text("Use any browser", fontSize = (30 * scale).sp, lineHeight = (43.5f * scale).sp, fontWeight = FontWeight.Bold)
        TvPairingInstruction("1", "Go to", scale)
        Text(session?.browserAddress ?: if (busy) "Creating a code…" else "Your server", Modifier.padding(start = 62.dp * scale),
            fontSize = (44 * scale).sp, lineHeight = (63.8f * scale).sp, fontWeight = FontWeight.Bold)
        TvPairingInstruction("2", "Enter this code", scale)
        Box(Modifier.padding(start = 62.dp * scale).size(640.dp * scale, 120.dp * scale)
            .background(Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp * scale))
            .border(1.dp * scale, Color.White.copy(alpha = .12f), RoundedCornerShape(16.dp * scale)), contentAlignment = Alignment.Center) {
            Text(session?.code ?: if (busy) "Waiting…" else "No code", fontSize = ((if (session == null) 36 else 72) * scale).sp,
                lineHeight = (86 * scale).sp, letterSpacing = (5.76f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.padding(start = 62.dp * scale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            session?.expiryLabel?.let { Text("Code expires in $it", fontSize = (22 * scale).sp, lineHeight = (32 * scale).sp, color = Color(0xFF8F8D89)) }
            TvAction(Modifier.height(48.dp * scale), scale, requester = first, activate = refresh, cornerRadius = 10.dp * scale,
                contentPadding = PaddingValues(horizontal = 18.dp * scale)) {
                Icon(Lucide.RotateCcw, null, Modifier.size(24.dp * scale), it); Spacer(Modifier.width(10.dp * scale))
                Text("New code", color = it, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TvPairingInstruction(number: String, label: String, scale: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        Box(Modifier.size(44.dp * scale).border(2.dp * scale, Color.White.copy(alpha = .3f), CircleShape), contentAlignment = Alignment.Center) {
            Text(number, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp, fontWeight = FontWeight.Bold)
        }
        Text(label, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, color = Palette.secondary)
    }
}
