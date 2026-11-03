package org.viptv.app.features.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import coil3.PlatformContext
import java.net.Inet4Address
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import org.viptv.app.smartcast.NativeSmartCast
import org.viptv.app.smartcast.SmartCastClient
import org.viptv.app.smartcast.SmartCastDiscovery
import org.viptv.app.smartcast.SmartCastTokenStore
import org.viptv.app.smartcast.smartCastDeviceId
import org.viptv.app.smartcast.smartCastDeviceName
import org.viptv.engine.wire.VizioControllerOutputKind
import org.viptv.engine.wire.VizioFailureKind
import org.viptv.engine.wire.VizioPairingChallenge

internal actual fun vizioSetupEngine(context: PlatformContext): VizioSetupEngine? = AndroidVizioSetupEngine(context)

internal fun vizioChallenge(result: Map<String, JsonElement>?): VizioPairingChallenge? {
    val challengeType = (result?.get("challengeType") as? JsonPrimitive)?.wholeNumber() ?: return null
    val token = (result?.get("token") as? JsonPrimitive)?.wholeNumber() ?: return null
    if (challengeType !in 0..65535L || token !in 0..4294967295L) return null
    return VizioPairingChallenge(challengeType.toInt(), token)
}

private fun JsonPrimitive.wholeNumber(): Long? {
    val value = doubleOrNull ?: return null
    val long = value.toLong()
    return if (long.toDouble() == value) long else null
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
    private val deviceId = smartCastDeviceId(context)

    override suspend fun search(limit: Int, onResult: (VizioTv) -> Unit) {
        val subnet = localSubnet() ?: throw VizioNoLocalNetwork()
        if (localNetworkDenied()) throw VizioNoAccess()
        discovery.search(subnet, limit) { onResult(it) }
    }

    override suspend fun probe(origin: String): VizioTv? {
        if (localSubnet() != null && localNetworkDenied()) throw VizioNoAccess()
        return discovery.probe(origin)
    }

    override suspend fun beginPair(tv: VizioTv): VizioPairingChallenge? = withContext(Dispatchers.IO) {
        val client = SmartCastClient.create(tv.origin, deviceId, smartCastDeviceName, tokens, pinCertificate = false)
        try {
            val output = client.run("beginPair")
            if (output.kind != VizioControllerOutputKind.COMPLETE) return@withContext null
            vizioChallenge(output.result)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { client.close() }
    }

    override suspend fun finishPair(tv: VizioTv, challenge: VizioPairingChallenge, pin: String): VizioPairResult =
        withContext(Dispatchers.IO) {
            val client = SmartCastClient.create(tv.origin, deviceId, smartCastDeviceName, tokens, pinCertificate = false)
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
            val client = SmartCastClient.create(tv.origin, deviceId, smartCastDeviceName, tokens, pinCertificate = false)
            try { client.run("cancelPair") } finally { client.close() }
        }
    }

    override fun openLocalNetworkSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun localNetworkDenied(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)
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