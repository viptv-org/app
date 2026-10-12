package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import org.viptv.app.components.Brand
import org.viptv.app.components.TvAction
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette
import com.composables.icons.lucide.*

@Composable
internal fun TvSignInChoice(scale: Float, local: (() -> Unit)?, select: (TvSignInStep) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Box(Modifier.fillMaxSize()) {
        Text("Sign in to VIPTV", Modifier.offset(96.dp * scale, 96.dp * scale), fontSize = (56 * scale).sp, lineHeight = (64 * scale).sp,
            letterSpacing = (-1.12f * scale).sp, fontWeight = FontWeight.Bold)
        Column(Modifier.offset(96.dp * scale, 250.dp * scale).width(900.dp * scale), verticalArrangement = Arrangement.spacedBy(16.dp * scale)) {
            val options = listOf(
                Triple(TvSignInStep.Phone, "Use your phone", "Scan a QR code — quickest"),
                Triple(TvSignInStep.Browser, "Use a browser", "Go to a web address and type a code"),
                Triple(TvSignInStep.Email, "Email me a code", "Type your email once, then a 6-digit code"),
                Triple(TvSignInStep.Password, "Use a password", null),
                Triple(TvSignInStep.Iptv, "IPTV provider", "Xtream Codes login or an M3U playlist"))
            options.forEachIndexed { index, (step, title, subtitle) ->
                TvEntryOption(scale, title, subtitle, if (index == 0) first else null,
                    { select(step) }) { tint -> Icon(when (step) { TvSignInStep.Phone -> Lucide.Smartphone; TvSignInStep.Browser -> Lucide.ExternalLink; TvSignInStep.Email -> Lucide.Mail; TvSignInStep.Password -> Lucide.KeyRound; else -> Lucide.Server }, null, Modifier.size(32.dp * scale), tint) }
            }
            if (local != null) TvEntryOption(scale, "Use without an account", "Everything stays on this TV", null, local) {
                Icon(Lucide.UserRound, null, Modifier.size(32.dp * scale), it)
            }
        }
        Column(Modifier.offset(1120.dp * scale, 250.dp * scale).width(704.dp * scale), verticalArrangement = Arrangement.spacedBy(24.dp * scale)) {
            Box(Modifier.fillMaxWidth().height(396.dp * scale).clip(RoundedCornerShape(18.dp * scale))) {
                AsyncImage(Res.getUri("files/entry/feature.jpg"), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)
                Box(Modifier.align(Alignment.BottomStart).padding(32.dp * scale)) { Brand(64.dp * scale, (72 * scale).sp, 18.dp * scale, (-2.88f * scale).sp, (72 * scale).sp) }
            }
            Text("All of these reach the same VIPTV account. You can add more ways to sign in later from your phone or computer.",
                color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        }
    }
}

@Composable
private fun TvEntryOption(scale: Float, title: String, subtitle: String?, requester: FocusRequester?, activate: () -> Unit,
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    TvAction(Modifier.fillMaxWidth().height(100.dp * scale), scale, requester = requester, activate = activate,
        contentPadding = PaddingValues(horizontal = 32.dp * scale), arrangement = Arrangement.Start, background = androidx.compose.ui.graphics.Color(0xFF1D1D1D)) { tint ->
        icon(tint)
        Spacer(Modifier.width(28.dp * scale))
        Column {
            Text(title, color = tint, fontSize = (30 * scale).sp, lineHeight = (36 * scale).sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = tint.copy(alpha = 0.75f), fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp) }
        }
    }
}
