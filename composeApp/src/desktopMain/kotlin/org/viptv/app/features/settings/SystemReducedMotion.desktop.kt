package org.viptv.app.features.settings

import coil3.PlatformContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.TimeUnit

private const val CLIENT_AREA_ANIMATION = 0x1042
private const val QUERY_TIMEOUT_MILLIS = 500L

internal actual fun systemReducedMotion(context: PlatformContext): Boolean = platformReducedMotion.reduced

internal class DesktopMotionPreference(query: () -> Boolean) {
    var reduced by mutableStateOf(true)
        private set

    init {
        thread(isDaemon = true, name = "viptv-motion-preference") {
            reduced = runCatching(query).getOrDefault(false)
        }
    }
}

private val platformReducedMotion by lazy {
    DesktopMotionPreference {
        when {
            Platform.isWindows() -> clientAreaAnimationsEnabled() == false
            Platform.isMac() -> macReducedMotion(query("defaults", "read", "com.apple.universalaccess", "reduceMotion"))
            else -> linuxReducedMotion(query("gsettings", "get", "org.gnome.desktop.interface", "enable-animations"))
        }
    }
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
