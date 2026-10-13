package org.viptv.app.player

internal actual fun reportNativePlaybackFailure(summary: String) {
    android.util.Log.w("VIPTV_PLAYER", summary)
}
