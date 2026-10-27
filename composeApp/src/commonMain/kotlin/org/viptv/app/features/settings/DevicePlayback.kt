package org.viptv.app.features.settings

import androidx.compose.runtime.*
import coil3.compose.LocalPlatformContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Device-local Playback choices. "Autoplay next episode" lives here because the
 * profile preference API has no field for it: the server rejects unknown fields
 * (app#65). Two devices on one account keep their own.
 */
@Serializable
internal data class DevicePlaybackPreferences(
    /** At the end of an episode the end panel counts down 10 s and plays the next one. */
    val autoplayNext: Boolean = true,
)

// A newer build may add fields; an older one must not throw the record away.
private val playbackJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** A missing or unreadable record falls back to the defaults; never throws. */
internal fun decodeDevicePlayback(raw: String?): DevicePlaybackPreferences = raw
    ?.let { runCatching { playbackJson.decodeFromString(DevicePlaybackPreferences.serializer(), it) }.getOrNull() }
    ?: DevicePlaybackPreferences()

/** Owns the record while a screen shows it; every change is written straight through. */
@Stable
internal class DevicePlaybackState(private val store: DeviceRecordStore) {
    var value by mutableStateOf(decodeDevicePlayback(runCatching { store.read() }.getOrNull()))
        private set

    fun change(next: DevicePlaybackPreferences) {
        value = next
        runCatching { store.write(playbackJson.encodeToString(DevicePlaybackPreferences.serializer(), next)) }
    }
}

@Composable
internal fun rememberDevicePlaybackState(): DevicePlaybackState {
    val context = LocalPlatformContext.current
    return remember(context) { DevicePlaybackState(deviceRecordStore(context, DeviceRecord.Playback)) }
}

