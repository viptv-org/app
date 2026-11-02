package org.viptv.app.features.remote

import android.content.Context
import coil3.PlatformContext
import kotlinx.serialization.json.JsonObject
import org.viptv.app.features.settings.watchOnTvAvailable
import org.viptv.app.smartcast.SmartCastClient
import org.viptv.app.smartcast.SmartCastTokenStore
import org.viptv.app.smartcast.smartCastDeviceId
import org.viptv.app.smartcast.smartCastDeviceName
import org.viptv.engine.wire.VizioControllerOutput

internal actual fun vizioRemoteEngine(context: PlatformContext, tvOrigin: String): VizioRemoteEngine? {
    if (!watchOnTvAvailable(context)) return null
    return AndroidVizioRemoteEngine(context, tvOrigin)
}

private class AndroidVizioRemoteEngine(context: Context, tvOrigin: String) : VizioRemoteEngine {
    private val client = SmartCastClient.create(tvOrigin, smartCastDeviceId(context), smartCastDeviceName, SmartCastTokenStore(context))

    override suspend fun run(operation: String, input: JsonObject): VizioControllerOutput = client.run(operation, input)

    override fun close() = client.close()
}