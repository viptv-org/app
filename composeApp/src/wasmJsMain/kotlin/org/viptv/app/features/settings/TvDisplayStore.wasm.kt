package org.viptv.app.features.settings

import coil3.PlatformContext
import kotlinx.browser.window

private const val KEY = "viptv.tv.display.v1"

/** localStorage throws when a browser blocks site data; a miss is not fatal. */
private object BrowserTvDisplayStore : TvDisplayStore {
    override fun read(): String? = runCatching { window.localStorage.getItem(KEY) }.getOrNull()

    override fun write(value: String) { runCatching { window.localStorage.setItem(KEY, value) } }
}

internal actual fun tvDisplayStore(context: PlatformContext): TvDisplayStore = BrowserTvDisplayStore
