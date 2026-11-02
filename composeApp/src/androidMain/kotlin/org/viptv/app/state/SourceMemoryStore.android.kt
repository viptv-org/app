package org.viptv.app.state

import android.content.Context

object AndroidSourceMemory {
    @Volatile private var context: Context? = null

    fun attach(context: Context) { this.context = context.applicationContext }

    internal fun store(): SourceMemoryStore? = context?.let(::AndroidSourceMemoryStore)
}

private class AndroidSourceMemoryStore(private val context: Context) : SourceMemoryStore {
    private val preferences get() = context.getSharedPreferences("source-memory", Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString("memory-v1", null)

    override fun write(value: String) { preferences.edit().putString("memory-v1", value).apply() }
}

internal actual fun platformSourceMemoryStore(): SourceMemoryStore = AndroidSourceMemory.store() ?: EmptySourceMemoryStore
