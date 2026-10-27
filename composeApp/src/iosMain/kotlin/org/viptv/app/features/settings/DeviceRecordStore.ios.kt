package org.viptv.app.features.settings

import coil3.PlatformContext
import platform.Foundation.NSUserDefaults

/** Mirrors RemoteSessionStore.ios.kt's NSUserDefaults shape; [context] is unused. Display keeps the key it shipped with. */
private class IosDeviceRecordStore(private val key: String) : DeviceRecordStore {
    override fun read(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)

    override fun write(value: String) { NSUserDefaults.standardUserDefaults.setObject(value, key) }
}

private val display = IosDeviceRecordStore("viptv.tv.display.v1")
private val playback = IosDeviceRecordStore("viptv.device.playback.v1")

internal actual fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore = when (record) {
    DeviceRecord.Display -> display
    DeviceRecord.Playback -> playback
}
