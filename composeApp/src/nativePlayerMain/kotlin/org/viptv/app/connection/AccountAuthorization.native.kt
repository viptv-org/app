package org.viptv.app.connection

import java.net.URI

internal const val ACCOUNT_BROWSER_UNAVAILABLE = "Open this address in a browser on your phone or computer."

/** Retain encoded OAuth query bytes; never dispatch non-web handlers or user info. */
internal fun accountAuthorizationUri(url: String): URI = try {
    URI(url).also { uri ->
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.port in -1..65535)
    }
} catch (_: Exception) { throw LocalFailure("Open a valid HTTPS address in your browser.") }

internal actual fun openAccountAuthorization(url: String) = launchNativeAccountBrowser(url)
internal expect fun launchNativeAccountBrowser(url: String)
