package org.viptv.app.state

import platform.Foundation.NSUserDefaults

private const val SOURCE_MEMORY_KEY = "viptv.source.memory.v1"

private object IosSourceMemoryStore : SourceMemoryStore {
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(SOURCE_MEMORY_KEY)

    override fun write(value: String) { NSUserDefaults.standardUserDefaults.setObject(value, SOURCE_MEMORY_KEY) }
}

internal actual fun platformSourceMemoryStore(): SourceMemoryStore = IosSourceMemoryStore
