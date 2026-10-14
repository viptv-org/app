package org.viptv.app.features.entry

import org.viptv.app.connection.IptvCredentials

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class TvSignInStep { Welcome, Choice, Phone, Browser, Email, EmailCode, Password, Iptv }

@Composable
internal fun TvSignIn(origin: String, busy: Boolean, error: String?, local: (() -> Unit)?, connect: (String, String, String) -> Unit,
    backChanged: ((() -> Unit)?) -> Unit, initialStep: TvSignInStep = TvSignInStep.Welcome, initialEmail: String = "",
    initialIptv: IptvCredentials = IptvCredentials(), emailChallenge: TvEmailCodeChallenge? = null, pairing: TvPairingSession? = null,
    remotePairing: org.viptv.app.connection.DevicePairingDisplay? = null, startPairing: ((String) -> Unit)? = null,
    cancelPairing: () -> Unit = {}, iptv: IptvEntryActions? = null) {
    var step by remember { mutableStateOf(initialStep) }
    var server by remember(origin) { mutableStateOf(origin) }
    val back = { step = when (step) { TvSignInStep.Choice -> TvSignInStep.Welcome; TvSignInStep.EmailCode -> TvSignInStep.Email; else -> TvSignInStep.Choice } }
    DisposableEffect(step) {
        backChanged(if (step == TvSignInStep.Welcome) null else back)
        onDispose { backChanged(null) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        when (step) {
            TvSignInStep.Welcome -> TvWelcome(scale, { step = TvSignInStep.Choice }, local)
            TvSignInStep.Choice -> TvSignInChoice(scale, local) { step = it }
            TvSignInStep.Password -> TvPasswordPanel(server, busy, error, scale, connect, { step = TvSignInStep.Email }, serverChanged = { server = it })
            TvSignInStep.Email -> TvEmailSignIn(scale, { step = TvSignInStep.Phone }, initialEmail, initialEmail.isNotEmpty())
            TvSignInStep.EmailCode -> if (emailChallenge != null) TvEmailCode(scale, emailChallenge, busy = busy)
                else TvEmailSignIn(scale, { step = TvSignInStep.Phone })
            TvSignInStep.Iptv -> TvIptvSignIn(scale, { step = TvSignInStep.Phone }, initialIptv, iptv, busy, error)
            TvSignInStep.Phone, TvSignInStep.Browser -> if (startPairing == null) TvPairingSignIn(scale, { step = TvSignInStep.Email }, pairing)
                else TvRemotePairing(server, remotePairing, busy, error, scale, { server = it }, startPairing, cancelPairing, back) { step = TvSignInStep.Email }
        }
    }
}
