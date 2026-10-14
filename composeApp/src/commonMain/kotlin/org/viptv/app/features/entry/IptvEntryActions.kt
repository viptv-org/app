package org.viptv.app.features.entry

import androidx.compose.runtime.Stable
import org.viptv.app.connection.IptvCredentials

@Stable
class IptvEntryActions(val connect: (IptvCredentials) -> Unit, val cancel: () -> Unit)
