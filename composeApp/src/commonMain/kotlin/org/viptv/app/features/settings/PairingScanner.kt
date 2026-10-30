package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext

internal expect fun pairingScannerAvailable(context: PlatformContext): Boolean

@Composable
internal fun rememberPairingScannerAvailable(): Boolean {
    val context = LocalPlatformContext.current
    return remember(context) { pairingScannerAvailable(context) }
}

@Composable
internal expect fun PairingScanner(onPayload: (String) -> Unit, dismiss: () -> Unit)
