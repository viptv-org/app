package org.viptv.app.player.mpv

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

/**
 * libmpv's client API parses and formats numbers with the C library, so it
 * requires the process-wide `LC_NUMERIC` locale to be `"C"`. The JVM calls
 * `setlocale(LC_ALL, "")` at startup and never restores `LC_NUMERIC`, which
 * means that on a normal Linux desktop (`LANG=en_US.UTF-8`, `de_DE.UTF-8`, …)
 * `mpv_create()` returns NULL and desktop playback cannot start at all.
 *
 * mpv prints the fix itself: `Call 'setlocale(LC_NUMERIC, "C");' in your code.`
 * That is all this object does. Java's own formatting is driven by
 * `java.util.Locale`, not libc, so the rest of the app is unaffected.
 */
internal object MpvLocale {
    private interface LibC : Library {
        fun setlocale(category: Int, locale: String?): String?
    }

    /** glibc numbers LC_NUMERIC 1; Darwin and the Windows CRT number it 4. */
    private val numericCategory = if (Platform.isLinux()) 1 else 4

    /**
     * Windows keeps one locale per CRT, and the bundled mpv may use either the
     * legacy `msvcrt` or the modern `ucrtbase` depending on how it was built.
     * Set both so the requirement holds whichever CRT libmpv links against.
     */
    private val libcs: List<LibC> by lazy {
        val names = if (Platform.isWindows()) listOf("msvcrt", "ucrtbase") else listOf(Platform.C_LIBRARY_NAME)
        names.mapNotNull { name -> runCatching { Native.load(name, LibC::class.java) }.getOrNull() }
    }

    /** The C library's current LC_NUMERIC name, or null if it cannot be read. */
    fun numericLocale(): String? = libcs.firstNotNullOfOrNull { it.setlocale(numericCategory, null) }

    /** Replace LC_NUMERIC with [name] in every loaded CRT; false when one rejects it. */
    fun setNumericLocale(name: String): Boolean =
        libcs.isNotEmpty() && libcs.all { it.setlocale(numericCategory, name) != null }

    /**
     * Assert the locale libmpv needs, immediately before creating a client.
     * Done per client rather than once per process so that any later libc
     * `setlocale` cannot silently bring the failure back.
     */
    fun requireNumericCLocale() {
        check(libcs.isNotEmpty()) { "Could not load the C library to set LC_NUMERIC=C; libmpv cannot create a client" }
        for (libc in libcs) {
            val applied = libc.setlocale(numericCategory, "C")
                ?: error("The C library refused to set LC_NUMERIC=C; libmpv cannot create a client")
            check(applied == "C") { "LC_NUMERIC is \"$applied\", libmpv requires \"C\"" }
        }
    }
}