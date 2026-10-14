package org.viptv.app.preview

import androidx.compose.runtime.Composable
import org.viptv.app.connection.DevicePairingDisplay
import org.viptv.app.features.entry.NativeDevicePairing

/** Offline layout fixture: never opens a browser or requests an actual device code. */
@Composable
internal fun PreviewNativePairing(desktop: Boolean, variant: String, back: () -> Unit) {
    val display = if (variant.endsWith("Loading")) null else DevicePairingDisplay(
        "https://example.invalid/device", "https://example.invalid/device?code=BDWP-HQPK", "BDWP-HQPK", 892)
    PreviewNativeEntryFrame(desktop, back) {
        NativeDevicePairing("https://example.invalid", display, true, null, !desktop,
            variant.startsWith("PairingBrowser"), {}, {}, {}, back, autoOpen = false)
    }
}
