package org.viptv.app.connection

import java.awt.Desktop

internal actual fun launchNativeAccountBrowser(url: String) {
    val uri = accountAuthorizationUri(url)
    try {
        if (!Desktop.isDesktopSupported()) throw LocalFailure(ACCOUNT_BROWSER_UNAVAILABLE)
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) throw LocalFailure(ACCOUNT_BROWSER_UNAVAILABLE)
        desktop.browse(uri)
    } catch (_: Exception) { throw LocalFailure(ACCOUNT_BROWSER_UNAVAILABLE) }
}
