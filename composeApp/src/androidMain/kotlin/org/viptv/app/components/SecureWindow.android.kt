package org.viptv.app.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import java.util.IdentityHashMap

private val secureHolders = IdentityHashMap<Window, Int>()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal actual fun SecureWindow(secure: Boolean, dialog: Boolean) {
    val view = LocalView.current
    val window = (if (dialog) (view.parent as? DialogWindowProvider)?.window else null)
        ?: view.context.findActivity()?.window
    DisposableEffect(window, secure) {
        if (secure && window != null) {
            val win = window
            val next = (secureHolders[win] ?: 0) + 1
            secureHolders[win] = next
            if (next == 1) win.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                val remaining = (secureHolders[win] ?: 1) - 1
                if (remaining <= 0) {
                    secureHolders.remove(win)
                    win.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    secureHolders[win] = remaining
                }
            }
        } else {
            onDispose { }
        }
    }
}