package org.viptv.app.connection

internal actual fun deviceDisplayName(): String {
    val platform = System.getProperty("os.name").let {
        when { it.startsWith("Windows") -> "Windows"; it.startsWith("Mac") -> "macOS"; else -> "Linux" }
    }
    return "VIPTV for $platform"
}
