package org.viptv.app.features.settings

import androidx.compose.runtime.*
import coil3.compose.LocalPlatformContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DevicePlaybackPreferences(
    val autoplayNext: Boolean = true,
)

// A newer build may add fields; an older one must not throw the record away.
private val playbackJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun decodeDevicePlayback(raw: String?): DevicePlaybackPreferences = raw
    ?.let { runCatching { playbackJson.decodeFromString(DevicePlaybackPreferences.serializer(), it) }.getOrNull() }
    ?: DevicePlaybackPreferences()

@Stable
internal class DevicePlaybackState(private val store: DeviceRecordStore) {
    var value by mutableStateOf(decodeDevicePlayback(runCatching { store.read() }.getOrNull()))
        private set

    fun setAutoplayNext(enabled: Boolean) = change(value.copy(autoplayNext = enabled))

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

