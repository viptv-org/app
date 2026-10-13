package org.viptv.app.connection

import kotlinx.browser.window

internal actual fun openAccountAuthorization(url: String) { window.location.assign(url) }
