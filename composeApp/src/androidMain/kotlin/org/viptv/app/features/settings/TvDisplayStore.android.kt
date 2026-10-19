package org.viptv.app.features.settings

import android.content.Context
import coil3.PlatformContext

private const val STORE = "tv-display"
private const val KEY = "display-v1"

/**
 * [PlatformContext] is the Android Context here, so the record needs no attach
 * hook at the Activity. `apply()` keeps the write off the frame the D-pad press
 * arrives on.
 */
private object AndroidTvDisplayStore(private val context: PlatformContext) : TvDisplayStore {
    private val preferences get() = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY, null)

    override fun write(value: String) { preferences.edit().putString(KEY, value).apply() }
}

internal actual fun tvDisplayStore(context: PlatformContext): TvDisplayStore = AndroidTvDisplayStore(context)
