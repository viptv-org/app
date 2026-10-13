package org.viptv.app.connection

internal actual fun nativePlaybackPlatform(): String = "desktop"
internal actual fun nativeDisplayBounds(): Pair<Int, Int> = java.awt.Toolkit.getDefaultToolkit().screenSize.let {
    it.width to it.height
}
