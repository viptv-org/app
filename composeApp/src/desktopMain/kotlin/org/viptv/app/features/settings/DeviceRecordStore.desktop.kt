package org.viptv.app.features.settings

import coil3.PlatformContext
import java.util.prefs.Preferences

// java.util.prefs gives the window its own per-user store; the Display node and key are the ones it shipped with.
private class DesktopDeviceRecordStore(node: String, private val key: String) : DeviceRecordStore {
    private val node = Preferences.userRoot().node(node)

    override fun read(): String? = node.get(key, null)

    override fun write(value: String) {
        node.put(key, value)
        node.flush()
    }
}

private val display by lazy { DesktopDeviceRecordStore("org/viptv/app/tv-display", "tv-display-v1") }
private val playback by lazy { DesktopDeviceRecordStore("org/viptv/app/device-playback", "playback-v1") }

internal actual fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore = when (record) {
    DeviceRecord.Display -> display
    DeviceRecord.Playback -> playback
}
