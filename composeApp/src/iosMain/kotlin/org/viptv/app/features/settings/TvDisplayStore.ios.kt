package org.viptv.app.features.settings

import coil3.PlatformContext
import platform.Foundation.NSUserDefaults

private const val KEY = "viptv.tv.display.v1"

/** Mirrors RemoteSessionStore.ios.kt's NSUserDefaults shape; [context] is unused. */
private object IosTvDisplayStore : TvDisplayStore {
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(KEY)

    override fun write(value: String) { NSUserDefaults.standardUserDefaults.setObject(value, KEY) }
}

internal actual fun tvDisplayStore(context: PlatformContext): TvDisplayStore = IosTvDisplayStore
