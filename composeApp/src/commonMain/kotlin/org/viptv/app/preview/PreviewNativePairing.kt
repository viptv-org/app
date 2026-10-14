package org.viptv.app.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.DevicePairingDisplay
import org.viptv.app.features.entry.DesktopSignInBackdrop
import org.viptv.app.features.entry.NativeDevicePairing
import org.viptv.app.features.entry.SignInBackBar

/** Offline layout fixture: never opens a browser or requests an actual device code. */
@Composable
internal fun PreviewNativePairing(desktop: Boolean, variant: String, back: () -> Unit) {
    val display = if (variant.endsWith("Loading")) null else DevicePairingDisplay(
        "https://example.invalid/device", "https://example.invalid/device?code=BDWP-HQPK", "BDWP-HQPK", 892)
    Box(Modifier.fillMaxSize()) {
        if (desktop) DesktopSignInBackdrop()
        val frame = if (desktop) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        Column(frame, horizontalAlignment = if (desktop) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = if (desktop) Arrangement.Center else Arrangement.Top) {
            if (!desktop) SignInBackBar(back)
            NativeDevicePairing("https://example.invalid", display, true, null, !desktop,
                variant.startsWith("PairingBrowser"), {}, {}, {}, back, autoOpen = false)
        }
    }
}
