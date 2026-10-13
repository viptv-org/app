package org.viptv.app.connection

internal actual fun nativePlaybackPlatform(): String = "android"
internal actual fun nativeDisplayBounds(): Pair<Int, Int> = android.content.res.Resources.getSystem().displayMetrics.let {
    it.widthPixels to it.heightPixels
}
