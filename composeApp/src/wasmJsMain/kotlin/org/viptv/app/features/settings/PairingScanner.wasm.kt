package org.viptv.app.features.settings

import androidx.compose.runtime.Composable
import coil3.PlatformContext

internal actual fun pairingScannerAvailable(context: PlatformContext): Boolean = false

@Composable
internal actual fun PairingScanner(onPayload: (String) -> Unit, dismiss: () -> Unit) = Unit
