package org.viptv.app.features.settings

import coil3.PlatformContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.TimeUnit

private const val CLIENT_AREA_ANIMATION = 0x1042
private const val QUERY_TIMEOUT_MILLIS = 500L

@Composable
internal actual fun systemReducedMotion(context: PlatformContext): Boolean =
    rememberDesktopMotion { platformReducedMotion }

@Composable
internal fun rememberDesktopMotion(query: () -> Boolean): Boolean =
    produceState(true, query) {
        value = withContext(Dispatchers.IO) { runCatching(query).getOrDefault(false) }
    }.value

private val platformReducedMotion: Boolean by lazy {
    runCatching {
        when {
            Platform.isWindows() -> clientAreaAnimationsEnabled() == false
            Platform.isMac() -> macReducedMotion(query("defaults", "read", "com.apple.universalaccess", "reduceMotion"))
            else -> linuxReducedMotion(query("gsettings", "get", "org.gnome.desktop.interface", "enable-animations"))
        }
    }.getOrDefault(false)
}

internal fun macReducedMotion(output: String?): Boolean = output?.trim() == "1"

internal fun linuxReducedMotion(output: String?): Boolean = output?.trim() == "false"

private fun query(vararg command: String): String? = runCatching {
    val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    if (!process.waitFor(QUERY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        process.destroyForcibly()
        return@runCatching null
    }
    process.inputStream.bufferedReader().use { it.readText() }
}.getOrNull()

private fun clientAreaAnimationsEnabled(): Boolean? = runCatching {
    val enabled = IntByReference(1)
    val read = Native.load("user32", User32::class.java).SystemParametersInfoW(CLIENT_AREA_ANIMATION, 0, enabled, 0)
    if (read != 0) enabled.value != 0 else null
}.getOrNull()

private interface User32 : StdCallLibrary {
    fun SystemParametersInfoW(action: Int, param: Int, value: IntByReference, winIni: Int): Int
}
