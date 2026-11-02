package org.viptv.app.state

import kotlinx.browser.window

private const val SOURCE_MEMORY_KEY = "viptv.source.memory.v1"

// localStorage throws when a browser blocks site data; a miss is not fatal.
private object BrowserSourceMemoryStore : SourceMemoryStore {
    override fun read(): String? = runCatching { window.localStorage.getItem(SOURCE_MEMORY_KEY) }.getOrNull()

    override fun write(value: String) { runCatching { window.localStorage.setItem(SOURCE_MEMORY_KEY, value) } }
}

internal actual fun platformSourceMemoryStore(): SourceMemoryStore = BrowserSourceMemoryStore
