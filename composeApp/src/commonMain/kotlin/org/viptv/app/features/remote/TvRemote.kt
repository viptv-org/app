package org.viptv.app.features.remote

import androidx.compose.runtime.Composable
import coil3.PlatformContext
import kotlinx.serialization.json.JsonObject
import org.viptv.engine.wire.VizioControllerOutput
import org.viptv.engine.wire.VizioRemoteKey

internal interface VizioRemoteEngine {
    suspend fun run(operation: String, input: JsonObject = JsonObject(emptyMap())): VizioControllerOutput
    fun close()
}

internal expect fun vizioRemoteEngine(context: PlatformContext, tvOrigin: String): VizioRemoteEngine?

internal const val remoteSwipeThresholdDp = 32

@Composable
internal expect fun RemoteKeepScreenOn(active: Boolean)

internal fun remoteSwipeKey(x: Float, y: Float, threshold: Float): VizioRemoteKey? {
    val horizontal = kotlin.math.abs(x)
    val vertical = kotlin.math.abs(y)
    if (kotlin.math.max(horizontal, vertical) < threshold) return null
    return if (horizontal > vertical) {
        if (x > 0) VizioRemoteKey.RIGHT else VizioRemoteKey.LEFT
    } else {
        if (y > 0) VizioRemoteKey.DOWN else VizioRemoteKey.UP
    }
}