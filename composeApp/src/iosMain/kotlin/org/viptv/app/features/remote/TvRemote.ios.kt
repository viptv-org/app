package org.viptv.app.features.remote

import androidx.compose.runtime.Composable
import coil3.PlatformContext

internal actual fun vizioRemoteEngine(context: PlatformContext, tvOrigin: String): VizioRemoteEngine? = null

@Composable
internal actual fun RemoteKeepScreenOn(active: Boolean) = Unit