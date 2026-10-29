package org.viptv.app.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

// libmpv parses numbers with the C library, so LC_NUMERIC must be "C".
internal object MpvLocale {
    private interface LibC : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    // glibc numbers LC_NUMERIC 1; Darwin and the Windows CRT number it 4.
    private val numericCategory = if (Platform.isLinux()) 1 else 4

    // Windows keeps one locale per CRT: set both the legacy msvcrt and the modern ucrtbase.
    private val libcs: List<LibC> by lazy {
        val names = if (Platform.isWindows()) listOf("msvcrt", "ucrtbase") else listOf(Platform.C_LIBRARY_NAME)
        names.mapNotNull { name -> runCatching { Native.load(name, LibC::class.java) }.getOrNull() }
    }

    fun numericLocale(): String? = libcs.firstNotNullOfOrNull { it.setlocale(numericCategory, null) }

    fun setNumericLocale(name: String): Boolean =
        libcs.isNotEmpty() && libcs.all { it.setlocale(numericCategory, name) != null }

    // Assert the locale immediately before creating each client: a later libc setlocale can undo it.
    fun requireNumericCLocale() {
        check(libcs.isNotEmpty()) { "Could not load the C library to set LC_NUMERIC=C; libmpv cannot create a client" }
        for (libc in libcs) {
            val applied = libc.setlocale(numericCategory, "C")
                ?: error("The C library refused to set LC_NUMERIC=C; libmpv cannot create a client")
            check(applied == "C") { "LC_NUMERIC is \"$applied\", libmpv requires \"C\"" }
        }
    }
}