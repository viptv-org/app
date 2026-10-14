package org.viptv.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.app.features.entry.*

/** Layout fixture with reserved domains; submission never configures an actual provider. */
@Composable
internal fun PreviewNativeIptv(desktop: Boolean, variant: String, back: () -> Unit) {
    val credentials = if (variant == "IptvM3u") IptvCredentials(IptvMode.M3u, "https://provider.example/list.m3u")
        else IptvCredentials(address = "http://provider.example:8080", username = "preview-user", password = "preview-only")
    PreviewNativeEntryFrame(desktop, back) {
        if (desktop) SignInPanel(480.dp) { IptvForm(back, false, null, IptvEntryActions({}, {}), false, credentials) }
        else IptvForm(back, false, null, IptvEntryActions({}, {}), true, credentials)
    }
}
