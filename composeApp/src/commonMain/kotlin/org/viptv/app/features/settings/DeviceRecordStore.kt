package org.viptv.app.features.settings

import coil3.PlatformContext

/** One record in, one record out; the platform decides where the string lives. */
internal interface DeviceRecordStore {
    fun read(): String?
    fun write(value: String)
}

/** Choices that belong to this device rather than to the profile: two devices on one account keep their own. */
internal enum class DeviceRecord { Display, Playback }

/**
 * [context] is the Android Context (SharedPreferences has no other attach point
 * in this module); desktop, iOS and the browser already have a per-user store
 * and ignore it.
 */
internal expect fun deviceRecordStore(context: PlatformContext, record: DeviceRecord): DeviceRecordStore

/** The device's own reduce-motion setting, where the platform exposes one. */
internal expect fun systemReducedMotion(context: PlatformContext): Boolean
