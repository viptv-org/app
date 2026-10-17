package org.viptv.app.connection

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Application context avoids retaining an Activity across recreation. */
object AndroidAccountAuthorization {
    @Volatile private var context: Context? = null
    fun attach(context: Context) { this.context = context.applicationContext }
    fun open(url: String) {
        val uri = accountAuthorizationUri(url)
        val webAddress = uri.toString().replaceRange(0, uri.scheme.length, "https")
        val owner = context ?: throw LocalFailure(ACCOUNT_BROWSER_UNAVAILABLE)
        try {
            // Starting directly also works when package visibility hides handlers
            // from resolveActivity. TVs without a browser take the UI fallback.
            owner.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webAddress))
                .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { throw LocalFailure(ACCOUNT_BROWSER_UNAVAILABLE) }
    }
}

internal actual fun launchNativeAccountBrowser(url: String) = AndroidAccountAuthorization.open(url)
