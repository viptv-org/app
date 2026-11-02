package org.viptv.app.player.mpv

import com.sun.jna.Memory
import kotlinx.serialization.json.*

private const val MPV_FORMAT_NODE = 6

// decoder-list names every codec the bundled FFmpeg can decode. A runtime
// hardware-decode answer would need a GPU probe, so this stays unknown.
internal fun mpvDecoderCodecs(): Set<String>? = try {
    MpvLocale.requireNumericCLocale()
    val api = MpvNative.api
    val handle = requireNotNull(api.mpv_create())
    try {
        fun option(name: String, value: String) = mpvCheck(api.mpv_set_option_string(handle, name, value))
        option("config", "no")
        option("load-scripts", "no")
        option("terminal", "no")
        option("input-default-bindings", "no")
        option("osc", "no")
        option("ytdl", "no")
        option("vo", "null")
        option("hwdec", "no")
        mpvCheck(api.mpv_initialize(handle))
        val node: JsonElement? = Memory(16).use { slot ->
            slot.clear()
            if (api.mpv_get_property(handle, "decoder-list", MPV_FORMAT_NODE, slot) < 0) null
            else try { mpvNode(slot) } finally { api.mpv_free_node_contents(slot) }
        }
        (node as? JsonArray)?.mapNotNull { entry ->
            (entry as? JsonObject)?.get("codec")?.jsonPrimitive?.contentOrNull
        }?.toSet()
    } finally {
        api.mpv_terminate_destroy(handle)
    }
} catch (_: Throwable) {
    null
}
