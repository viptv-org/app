package org.viptv.app.features.settings

import android.content.Context
import coil3.PlatformContext

// apply() keeps the write off the frame the D-pad press runs on.
private class AndroidDeviceRecordStore(private val context: PlatformContext, private val file: String, private val key: String) : DeviceRecordStore {
    private val preferences get() = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(key, null)

    override fun write(value: String) { preferences.edit().putString(key, value).apply() }
}

internal actual fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore = when (record) {
    DeviceRecord.Display -> AndroidDeviceRecordStore(context, "tv-display", "display-v1")
    DeviceRecord.Playback -> AndroidDeviceRecordStore(context, "device-playback", "playback-v1")
}
