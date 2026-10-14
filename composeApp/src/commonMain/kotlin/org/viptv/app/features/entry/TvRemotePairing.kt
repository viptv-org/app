package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Server
import org.viptv.app.components.TvAction
import org.viptv.app.connection.DevicePairingDisplay

/** Connects the supplied pairing layout to a cancellable, server-owned challenge. */
@Composable
internal fun TvRemotePairing(server: String, display: DevicePairingDisplay?, busy: Boolean, error: String?, scale: Float,
    serverChanged: (String) -> Unit, start: (String) -> Unit, cancel: () -> Unit, back: () -> Unit, email: () -> Unit) {
    val session = display?.let {
        TvPairingSession(it.qrUri, it.verificationUri.removePrefix("https://"), it.userCode,
            "${it.remainingSeconds / 60}:${(it.remainingSeconds % 60).toString().padStart(2, '0')}")
    }
    DevicePairingRequest(server, serverChanged, start, cancel, back) { refresh, changeServer ->
      Box(Modifier.fillMaxSize()) {
        TvPairingSignIn(scale, email, session, refresh = refresh, busy = busy, error = error)
        TvAction(Modifier.offset(1404.dp * scale, 96.dp * scale).size(420.dp * scale, 72.dp * scale), scale,
            activate = changeServer) {
            Icon(Lucide.Server, null, Modifier.size(26.dp * scale), it)
            Spacer(Modifier.width(12.dp * scale))
            Text("Use another server", color = it, fontSize = (24 * scale).sp)
        }
      }
    }
}
