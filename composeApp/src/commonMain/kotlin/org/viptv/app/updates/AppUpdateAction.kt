package org.viptv.app.updates

// The server serves the web bundle, so reloading the page is the web's own update path.
internal expect fun reloadAppAction(): (() -> Unit)?

internal fun appUpdateAction(updater: AppUpdater?): (() -> Unit)? =
    updater?.let { app -> { app.updateNow() } } ?: reloadAppAction()
