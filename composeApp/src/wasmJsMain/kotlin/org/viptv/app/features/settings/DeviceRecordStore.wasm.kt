package org.viptv.app.features.settings

import coil3.PlatformContext
import kotlinx.browser.window

/** localStorage throws when a browser blocks site data; a miss is not fatal. Display keeps the key it shipped with. */
private class BrowserDeviceRecordStore(private val key: String) : DeviceRecordStore {
    override fun read(): String? = runCatching { window.localStorage.getItem(key) }.getOrNull()

    override fun write(value: String) { runCatching { window.localStorage.setItem(key, value) } }
}

private val display = BrowserDeviceRecordStore("viptv.tv.display.v1")
private val playback = BrowserDeviceRecordStore("viptv.device.playback.v1")

internal actual fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore = when (record) {
    DeviceRecord.Display -> display
    DeviceRecord.Playback -> playback
}
