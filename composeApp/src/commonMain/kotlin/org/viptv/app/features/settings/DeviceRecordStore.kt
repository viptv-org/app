package org.viptv.app.features.settings

import coil3.PlatformContext

internal interface DeviceRecordStore {
    fun read(): String?
    fun write(value: String)
}

internal enum class DeviceRecord { Display, Playback }

internal expect fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore

internal expect fun systemReducedMotion(context: PlatformContext): Boolean
