package org.viptv.app.features.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.IptvCredentials

@Composable
internal fun DesktopIptvPanel(back: () -> Unit, busy: Boolean = false, error: String? = null,
    connect: IptvEntryActions? = null) {
    SignInPanel(480.dp) { IptvForm(back, busy, error, connect, mobile = false) }
}
