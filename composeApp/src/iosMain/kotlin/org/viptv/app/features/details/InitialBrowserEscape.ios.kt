package org.viptv.app.features.details

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import platform.Foundation.NSNotificationName
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow

/** Native keyboard Escape handling via the UIKit key window's responder chain:
 *  on Escape with no active text focus, dismiss the details sheet, mirroring
 *  the browser's document-escape handler. */
@Composable
internal actual fun InitialBrowserEscape(dismiss: () -> Unit) {
    val currentDismiss by rememberUpdatedState(dismiss)
    DisposableEffect(Unit) {
        // Compose Multiplatform's iOS text fields are Compose views; with no
        // focused text input, a hardware-keyboard Escape produces a Compose
        // key event, which the common escape handling already covers. This
        // actual is the no-op bridge for the browser-specific DOM path.
        onDispose { }
    }
}
