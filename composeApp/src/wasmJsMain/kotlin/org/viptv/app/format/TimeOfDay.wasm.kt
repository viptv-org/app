@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.format

@JsFun("""() => Intl.DateTimeFormat().resolvedOptions().hourCycle ?? ''""")
private external fun resolvedHourCycle(): String

internal actual fun is24HourTime(): Boolean = when (resolvedHourCycle()) {
    "h23", "h24" -> true
    else -> false
}
