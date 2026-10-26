package org.viptv.app.player.mpv

import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.viptv.app.player.PlaybackInput
import java.nio.file.Files
import java.nio.file.Path

/** Packaging gate: load verified native bytes, create/free renderers and join control threads. */
internal fun verifyPackagedMpv() = runBlocking {
    repeat(4) {
        val session = MpvSession(PlaybackInput("mpv-packaging", "about:blank"))
        try {
            MpvParams().use { p ->
                p.add(1, p.text("sw"))
                val result = PointerByReference()
                mpvCheck(session.api.mpv_render_context_create(result, session.handle, p.pointer()))
                session.api.mpv_render_context_free(requireNotNull(result.value))
            }
        } finally { session.closeAfterRenderer() }
        withTimeout(5_000) { session.closed.await() }
    }
    Files.writeString(Path.of(requireNotNull(System.getenv("VIPTV_MPV_SMOKE_TEST"))), "verified-mpv-runtime")
}
