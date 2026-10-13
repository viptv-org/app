package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.viptv.app.components.TvAction
import org.viptv.app.theme.Palette

internal enum class TvSignInStep { Welcome, Choice, Phone, Browser, Email, Password, Iptv }

@Composable
internal fun TvSignIn(origin: String, busy: Boolean, error: String?, local: (() -> Unit)?, connect: (String, String, String) -> Unit,
    backChanged: ((() -> Unit)?) -> Unit, initialStep: TvSignInStep = TvSignInStep.Welcome, initialEmail: String = "",
    initialIptv: TvIptvDraft = TvIptvDraft()) {
    var step by remember { mutableStateOf(initialStep) }
    val back = { step = if (step == TvSignInStep.Choice) TvSignInStep.Welcome else TvSignInStep.Choice }
    DisposableEffect(step) {
        backChanged(if (step == TvSignInStep.Welcome) null else back)
        onDispose { backChanged(null) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        when (step) {
            TvSignInStep.Welcome -> TvWelcome(scale, { step = TvSignInStep.Choice }, local)
            TvSignInStep.Choice -> TvSignInChoice(scale, local) { step = it }
            TvSignInStep.Password -> TvPasswordPanel(origin, busy, error, scale, connect, { step = TvSignInStep.Email })
            TvSignInStep.Email -> TvEmailSignIn(scale, { step = TvSignInStep.Phone }, initialEmail, initialEmail.isNotEmpty())
            TvSignInStep.Iptv -> TvIptvSignIn(scale, { step = TvSignInStep.Phone }, initialIptv)
            else -> Column(Modifier.padding(96.dp * scale), verticalArrangement = Arrangement.spacedBy(28.dp * scale)) {
                Text(when (step) { TvSignInStep.Phone -> "Use your phone"; TvSignInStep.Browser -> "Use a browser"; TvSignInStep.Email -> "Email me a code"; else -> "IPTV provider" },
                    fontSize = (52 * scale).sp, fontWeight = FontWeight.Bold)
                Text("This sign-in method is not available on this connection yet.", color = Palette.secondary, fontSize = (26 * scale).sp)
                TvAction(Modifier.width(400.dp * scale).height(72.dp * scale), scale, primary = true, activate = { step = TvSignInStep.Password }) {
                    Text("Use a password", fontSize = (26 * scale).sp, color = it, fontWeight = FontWeight.Bold)
                }
                TvAction(Modifier.width(400.dp * scale).height(72.dp * scale), scale, activate = back) {
                    Text("All sign-in options", fontSize = (26 * scale).sp, color = it)
                }
            }
        }
    }
}
