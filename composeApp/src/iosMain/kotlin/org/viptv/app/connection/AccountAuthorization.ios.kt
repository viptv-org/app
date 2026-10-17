package org.viptv.app.connection

import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

internal actual fun openAccountAuthorization(url: String) {
    // ASWebAuthenticationSession requires an async completion dance and a
    // presentation anchor; the Simkl OAuth flow on the backend finishes with a
    // redirect the in-app browser must show. v1 uses SFSafariViewController
    // for the same user-visible flow as the browser's window.location.assign.
    val nsurl = NSURL.URLWithString(url) ?: return
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    root?.presentViewController(SFSafariViewController(uRL = nsurl), animated = true) {}
}
