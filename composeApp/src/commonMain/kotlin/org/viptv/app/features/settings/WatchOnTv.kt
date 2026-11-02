package org.viptv.app.features.settings

import androidx.compose.runtime.*
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class WatchOnTvPreferences(
    val optedIn: Boolean = false,
    val tvName: String? = null,
    val tvOrigin: String? = null,
)

// A newer build may add remote preferences; an older build must not throw the record away.
private val watchOnTvJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun decodeWatchOnTv(raw: String?): WatchOnTvPreferences = raw
    ?.let { runCatching { watchOnTvJson.decodeFromString(WatchOnTvPreferences.serializer(), it) }.getOrNull() }
    ?: WatchOnTvPreferences()

@Stable
internal class WatchOnTvState(private val store: DeviceRecordStore) {
    var value by mutableStateOf(decodeWatchOnTv(runCatching { store.read() }.getOrNull()))
        private set

    fun optIn(tvName: String, tvOrigin: String) = change(WatchOnTvPreferences(optedIn = true, tvName = tvName, tvOrigin = tvOrigin))

    fun optOut() = change(WatchOnTvPreferences())

    fun change(next: WatchOnTvPreferences) {
        value = next
        runCatching { store.write(watchOnTvJson.encodeToString(WatchOnTvPreferences.serializer(), next)) }
    }
}

@Composable
internal fun rememberWatchOnTvState(): WatchOnTvState {
    val context = LocalPlatformContext.current
    return remember(context) { WatchOnTvState(deviceRecordStore(context, DeviceRecord.WatchOnTv)) }
}

internal expect fun watchOnTvAvailable(context: PlatformContext): Boolean

@Composable
internal fun rememberWatchOnTvAvailable(): Boolean {
    val context = LocalPlatformContext.current
    return remember(context) { watchOnTvAvailable(context) }
}

internal const val watchOnTvOff = "Off"
internal const val watchOnTvSubtitle = "Use this phone as a Vizio TV remote"

internal fun watchOnTvValue(preferences: WatchOnTvPreferences): String =
    if (preferences.optedIn && preferences.tvName != null) preferences.tvName else watchOnTvOff