package org.viptv.app.features.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import coil3.PlatformContext
import java.net.Inet4Address
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.viptv.app.smartcast.NativeSmartCast
import org.viptv.app.smartcast.SmartCastClient
import org.viptv.app.smartcast.SmartCastDiscovery
import org.viptv.app.smartcast.SmartCastTokenStore
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioPairingChallenge

internal actual fun vizioSetupEngine(context: PlatformContext): VizioSetupEngine? = AndroidVizioSetupEngine(context)

internal fun vizioChallenge(result: Map<String, JsonElement>?): VizioPairingChallenge? {
    val challengeType = (result?.get("challengeType") as? JsonPrimitive)?.intOrNull ?: return null
    val token = (result?.get("token") as? JsonPrimitive)?.longOrNull ?: return null
    return VizioPairingChallenge(challengeType, token)
}

internal fun vizioFinishResult(kind: VizioControllerOutputKind, failure: VizioFailureKind?): VizioPairResult = when (kind) {
    VizioControllerOutputKind.COMPLETE -> VizioPairResult.Paired
    VizioControllerOutputKind.ERROR -> when (failure) {
        VizioFailureKind.AUTHENTICATION, VizioFailureKind.INVALIDPARAMETER -> VizioPairResult.Rejected
        else -> VizioPairResult.Failed
    }
    else -> VizioPairResult.Failed
}

private class AndroidVizioSetupEngine(private val context: Context) : VizioSetupEngine {
    private val tokens = SmartCastTokenStore(context)
    private val native = NativeSmartCast
    private val discovery = SmartCastDiscovery(native)
    private val deviceId = context.getSharedPreferences("viptv_smartcast_pairing", Context.MODE_PRIVATE)
        .let { prefs ->
            if (!prefs.contains("deviceId")) prefs.edit().putString("deviceId", UUID.randomUUID().toString()).apply()
            prefs.getString("deviceId", null) ?: UUID.randomUUID().toString()
        }

    override suspend fun search(limit: Int, onResult: (VizioTv) -> Unit) {
        val subnet = localSubnet() ?: throw VizioNoLocalNetwork()
        discovery.search(subnet, limit) { onResult(it) }
    }

    override suspend fun probe(origin: String): VizioTv? = discovery.probe(origin)

    override suspend fun beginPair(tv: VizioTv): VizioPairingChallenge? = withContext(Dispatchers.IO) {
        val client = SmartCastClient.create(tv.origin, deviceId, "VIPTV phone", tokens)
        try {
            val output = client.run("beginPair")
            if (output.kind != VizioControllerOutputKind.COMPLETE) return@withContext null
            vizioChallenge(output.result)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { client.close() }
    }

    override suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult =
        withContext(Dispatchers.IO) {
            val client = SmartCastClient.create(tv.origin, deviceId, "VIPTV phone", tokens)
            try {
                val input = buildJsonObject {
                    put("challengeType", challenge.challengeType)
                    put("token", challenge.token)
                    put("pin", pin)
                }
                val output = client.run("finishPair", input)
                vizioFinishResult(output.kind, output.error?.kind)
            } finally { client.close() }
        }

    override suspend fun cancelPair(tv: VizioTv) {
        withContext(Dispatchers.IO) {
            val client = SmartCastClient.create(tv.origin, deviceId, "VIPTV phone", tokens)
            try { client.run("cancelPair") } finally { client.close() }
        }
    }

    private fun localSubnet(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = connectivity.allNetworks.firstOrNull {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        val address = connectivity.getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress ?: return null
        return address.substringBeforeLast('.')
    }
}