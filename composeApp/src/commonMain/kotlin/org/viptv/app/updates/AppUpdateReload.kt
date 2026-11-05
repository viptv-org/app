package org.viptv.app.updates

// The server serves the web bundle, so a reload picks up the build it now asks for; a platform whose app arrives
// outside the server has no in-app delivery path and offers no action.
internal expect fun reloadAppAction(): (() -> Unit)?

internal fun appUpdateAction(updater: AppUpdater?): (() -> Unit)? =
    updater?.let { app -> { app.updateNow() } } ?: reloadAppAction()
