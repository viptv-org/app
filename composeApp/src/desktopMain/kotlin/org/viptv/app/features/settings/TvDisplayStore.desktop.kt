package org.viptv.app.features.settings

import coil3.PlatformContext
import java.util.prefs.Preferences

private const val KEY = "tv-display-v1"

/** java.util.prefs gives the window its own per-user store; [context] is unused. */
private object DesktopTvDisplayStore : TvDisplayStore {
    private val node = Preferences.userRoot().node("org/viptv/app/tv-display")

    override fun read(): String? = node.get(KEY, null)

    override fun write(value: String) {
        node.put(KEY, value)
        node.flush()
    }
}

internal actual fun tvDisplayStore(context: PlatformContext): TvDisplayStore = DesktopTvDisplayStore
