package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class TvSignInStep { Welcome, Choice, Phone, Browser, Password }

@Composable
internal fun TvSignIn(origin: String, busy: Boolean, error: String?, local: (() -> Unit)?, connect: (String, String, String) -> Unit,
    backChanged: ((() -> Unit)?) -> Unit, initialStep: TvSignInStep = TvSignInStep.Welcome, pairing: TvPairingSession? = null,
    remotePairing: org.viptv.app.connection.DevicePairingDisplay? = null, startPairing: ((String) -> Unit)? = null,
    cancelPairing: () -> Unit = {}) {
    var step by remember { mutableStateOf(initialStep) }
    var server by remember(origin) { mutableStateOf(origin) }
    val back = { step = when (step) { TvSignInStep.Choice -> TvSignInStep.Welcome; else -> TvSignInStep.Choice } }
    DisposableEffect(step) {
        backChanged(if (step == TvSignInStep.Welcome) null else back)
        onDispose { backChanged(null) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth / 1920.dp
        when (step) {
            TvSignInStep.Welcome -> TvWelcome(scale, { step = TvSignInStep.Choice }, local, error)
            TvSignInStep.Choice -> TvSignInChoice(scale, local) { step = it }
            TvSignInStep.Password -> TvPasswordPanel(server, busy, error, scale, connect, serverChanged = { server = it })
            TvSignInStep.Phone, TvSignInStep.Browser -> if (startPairing == null) TvPairingSignIn(scale, pairing)
                else TvRemotePairing(server, remotePairing, busy, error, scale, { server = it }, startPairing, cancelPairing, back)
        }
    }
}
