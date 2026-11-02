package org.viptv.app.features.settings

import android.content.res.Configuration
import coil3.PlatformContext

internal actual fun watchOnTvAvailable(context: PlatformContext): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION