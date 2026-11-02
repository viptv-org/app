package org.viptv.app.state

import java.util.prefs.Preferences

private class DesktopSourceMemoryStore : SourceMemoryStore {
    private val node = Preferences.userRoot().node("org/viptv/app/source-memory")

    override fun read(): String? = node.get("memory-v1", null)

    override fun write(value: String) {
        node.put("memory-v1", value)
        node.flush()
    }
}

private val desktopSourceMemory by lazy { DesktopSourceMemoryStore() }

internal actual fun platformSourceMemoryStore(): SourceMemoryStore = desktopSourceMemory
