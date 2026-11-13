package org.viptv.app.features.settings

import coil3.PlatformContext
import androidx.compose.runtime.Composable

internal interface DeviceRecordStore {
    fun read(): String?
    fun write(value: String)
}

internal enum class DeviceRecord { Display, Playback, WatchOnTv }

internal expect fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore

@Composable
internal expect fun systemReducedMotion(context: PlatformContext): Boolean
