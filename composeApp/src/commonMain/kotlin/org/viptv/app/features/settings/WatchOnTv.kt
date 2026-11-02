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
    val remoteSwipe: Boolean = false,
    val tipDismissed: Boolean = false,
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
    var repairTv by mutableStateOf<VizioTv?>(null)
        private set

    fun optIn(tvName: String, tvOrigin: String) = change(value.copy(optedIn = true, tvName = tvName, tvOrigin = tvOrigin))

    fun optOut() = change(WatchOnTvPreferences())

    fun dismissRemoteTip() = change(value.copy(tipDismissed = true))

    fun setRemoteSwipe(swipe: Boolean) = change(value.copy(remoteSwipe = swipe))

    fun beginRepair(tv: VizioTv) { repairTv = tv }

    fun clearRepair() { repairTv = null }

    fun pairedTv(): VizioTv? = value.let { prefs ->
        if (prefs.optedIn && prefs.tvName != null && prefs.tvOrigin != null) VizioTv(prefs.tvName, prefs.tvOrigin) else null
    }

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

// One instance for the whole app: the Settings row and the header remote button observe the same record.
internal val LocalWatchOnTv = staticCompositionLocalOf<WatchOnTvState?> { null }