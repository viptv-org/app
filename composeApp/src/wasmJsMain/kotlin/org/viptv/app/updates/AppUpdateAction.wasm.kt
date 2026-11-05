package org.viptv.app.updates

import kotlinx.browser.window

internal actual fun reloadAppAction(): (() -> Unit)? = { window.location.reload() }
